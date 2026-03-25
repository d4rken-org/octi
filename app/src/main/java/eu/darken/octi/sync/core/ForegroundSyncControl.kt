package eu.darken.octi.sync.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.syncs.kserver.core.KServerConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundSyncControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val syncManager: SyncManager,
    private val syncExecutor: SyncExecutor,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
    private val networkStateProvider: NetworkStateProvider,
) {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    @Volatile private var lastBackgroundedAt: Instant? = null
    private var eventCollectorJob: Job? = null
    private var connectJob: Job? = null
    private var disconnectJob: Job? = null

    fun start() {
        log(TAG) { "start()" }

        val isForeground = MutableStateFlow(false)
        (context as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var startedCount = 0
            override fun onActivityStarted(activity: Activity) {
                startedCount++
                isForeground.value = true
                log(TAG) { "Activity started (count=$startedCount), isForeground=true" }
            }

            override fun onActivityStopped(activity: Activity) {
                startedCount--
                if (startedCount <= 0) {
                    startedCount = 0
                    isForeground.value = false
                    log(TAG) { "All activities stopped, isForeground=false" }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        combine(
            isForeground,
            syncSettings.foregroundSyncEnabled.flow,
            upgradeRepo.upgradeInfo,
            syncSettings.backgroundSyncOnMobile.flow,
            networkStateProvider.networkState,
        ) { foreground, enabled, info, syncOnMobile, networkState ->
            val isPro = info.isPro
            val networkOk = syncOnMobile || !networkState.isMeteredConnection
            val isActive = foreground && enabled && isPro && networkOk
            log(TAG) { "combine: foreground=$foreground, enabled=$enabled, isPro=$isPro, networkOk=$networkOk -> isActive=$isActive" }
            isActive
        }
            .distinctUntilChanged()
            .onEach { isActive ->
                _isActive.value = isActive
                if (isActive) {
                    onForeground()
                } else {
                    onBackground()
                }
            }
            .setupCommonEventHandlers(TAG) { "foreground-sync" }
            .launchIn(scope)
    }

    private fun onForeground() {
        log(TAG, INFO) { "onForeground()" }

        // Connect WebSocket on KServer connectors
        disconnectJob?.cancel()
        connectJob = scope.launch {
            syncManager.connectors.first()
                .filterIsInstance<KServerConnector>()
                .forEach { connector ->
                    log(TAG) { "Connecting WebSocket for ${connector.identifier}" }
                    connector.connectWebSocket()
                }
        }

        // Catch-up sync if backgrounded for > 2 minutes
        scope.launch {
            val backgroundedAt = lastBackgroundedAt
            if (backgroundedAt != null) {
                val away = Duration.between(backgroundedAt, Instant.now())
                if (away > CATCHUP_THRESHOLD) {
                    log(TAG, INFO) { "Away for ${away.seconds}s, running catch-up sync" }
                    syncExecutor.execute("ForegroundCatchUp")
                } else {
                    log(TAG) { "Away for ${away.seconds}s, skipping catch-up" }
                }
            }
            lastBackgroundedAt = null
        }

        // Start collecting sync events with channel-based batching
        eventCollectorJob = scope.launch {
            val eventChannel = Channel<SyncEvent.ModuleChanged>(Channel.UNLIMITED)

            // Producer: collect sync events, filter to UPDATED, send to channel
            val producerJob = launch {
                syncManager.syncEvents.collect { event ->
                    when (event) {
                        is SyncEvent.ModuleChanged -> when (event.action) {
                            SyncEvent.ModuleChanged.Action.UPDATED -> {
                                log(TAG) { "Queued: ${event.moduleId} on ${event.connectorId}" }
                                eventChannel.send(event)
                            }

                            SyncEvent.ModuleChanged.Action.DELETED -> {
                                log(TAG, INFO) { "Module deleted: ${event.moduleId}" }
                            }
                        }

                        is SyncEvent.BlobChanged -> {
                            log(TAG) { "Blob changed: ${event.blobKey} (${event.action})" }
                        }
                    }
                }
            }

            // Consumer: batch events within a timeout window, then sync
            try {
                while (isActive) {
                    val first = eventChannel.receive()
                    val pending = mutableMapOf<ConnectorId, MutableSet<ModuleId>>()
                    pending.getOrPut(first.connectorId) { mutableSetOf() }.add(first.moduleId)

                    // Drain events arriving within the debounce window
                    withTimeoutOrNull(CLIENT_DEBOUNCE_MS) {
                        while (true) {
                            val event = eventChannel.receive()
                            pending.getOrPut(event.connectorId) { mutableSetOf() }.add(event.moduleId)
                        }
                    }

                    syncPendingModules(pending)
                }
            } finally {
                // Flush remaining events on cancellation (W8)
                val remaining = mutableMapOf<ConnectorId, MutableSet<ModuleId>>()
                while (true) {
                    val event = eventChannel.tryReceive().getOrNull() ?: break
                    remaining.getOrPut(event.connectorId) { mutableSetOf() }.add(event.moduleId)
                }
                if (remaining.isNotEmpty()) {
                    log(TAG, INFO) { "Flushing ${remaining.values.sumOf { it.size }} pending events on shutdown" }
                    withContext(NonCancellable) { syncPendingModules(remaining) }
                }
                producerJob.cancel()
            }
        }
    }

    private suspend fun syncPendingModules(pending: Map<ConnectorId, Set<ModuleId>>) {
        pending.forEach { (connectorId, modules) ->
            log(TAG) { "Batched sync: ${modules.size} modules on $connectorId" }
            try {
                syncManager.sync(
                    connectorId,
                    SyncOptions(
                        stats = false,
                        readData = true,
                        writeData = false,
                        moduleFilter = modules.toSet(),
                    ),
                )
            } catch (e: Exception) {
                log(TAG, ERROR) { "Batched sync failed: ${e.asLog()}" }
            }
        }
    }

    private fun onBackground() {
        log(TAG, INFO) { "onBackground()" }
        lastBackgroundedAt = Instant.now()

        // Stop collecting events (also stops GDrive polling since the flow is cold)
        eventCollectorJob?.cancel()
        eventCollectorJob = null

        // Disconnect WebSocket on KServer connectors
        connectJob?.cancel()
        disconnectJob = scope.launch {
            syncManager.connectors.first()
                .filterIsInstance<KServerConnector>()
                .forEach { connector ->
                    log(TAG) { "Disconnecting WebSocket for ${connector.identifier}" }
                    connector.disconnectWebSocket()
                }
        }
    }

    companion object {
        private const val CLIENT_DEBOUNCE_MS = 500L
        private val CATCHUP_THRESHOLD = Duration.ofMinutes(2)
        private val TAG = logTag("Sync", "Foreground", "Control")
    }
}
