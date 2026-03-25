package eu.darken.octi.sync.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.syncs.kserver.core.KServerConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import eu.darken.octi.common.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

    @Volatile private var lastBackgroundedAt: Instant? = null
    private var eventCollectorJob: Job? = null

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
        scope.launch {
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

        // Start collecting sync events with accumulator + debounce
        eventCollectorJob = scope.launch {
            var debounceJob: Job? = null
            val pendingModules = mutableMapOf<ConnectorId, MutableSet<ModuleId>>()

            syncManager.syncEvents.collect { event ->
                when (event) {
                    is SyncEvent.ModuleChanged -> when (event.action) {
                        SyncEvent.ModuleChanged.Action.UPDATED -> {
                            pendingModules.getOrPut(event.connectorId) { mutableSetOf() }
                                .add(event.moduleId)
                            log(TAG) { "Queued: ${event.moduleId} on ${event.connectorId}" }
                        }

                        SyncEvent.ModuleChanged.Action.DELETED -> {
                            log(TAG, INFO) { "Module deleted: ${event.moduleId}" }
                        }
                    }

                    is SyncEvent.BlobChanged -> {
                        log(TAG) { "Blob changed: ${event.blobKey} (${event.action})" }
                    }
                }

                debounceJob?.cancel()
                debounceJob = launch {
                    delay(CLIENT_DEBOUNCE_MS)
                    pendingModules.forEach { (connectorId, modules) ->
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
                    pendingModules.clear()
                }
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
        scope.launch {
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
