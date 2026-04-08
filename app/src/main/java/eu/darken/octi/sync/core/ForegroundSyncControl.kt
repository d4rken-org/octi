package eu.darken.octi.sync.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Singleton
class ForegroundSyncControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val syncManager: SyncManager,
    private val syncExecutor: SyncExecutor,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
    private val networkStateProvider: NetworkStateProvider,
) {

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    @Volatile private var lastBackgroundedAt: Instant? = null
    @Volatile private var eventCollectorJob: Job? = null
    @Volatile private var backgroundDebounceJob: Job? = null
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        log(TAG) { "start()" }

        val isForeground = MutableStateFlow(false)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                backgroundDebounceJob?.cancel()
                backgroundDebounceJob = null
                isForeground.value = true
                log(TAG) { "Process foregrounded, isForeground=true" }
            }

            override fun onStop(owner: LifecycleOwner) {
                backgroundDebounceJob?.cancel()
                backgroundDebounceJob = scope.launch {
                    log(TAG) { "Process backgrounded, debouncing $BACKGROUND_DEBOUNCE..." }
                    delay(BACKGROUND_DEBOUNCE)
                    isForeground.value = false
                    log(TAG) { "Background debounce elapsed, isForeground=false" }
                }
            }
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

        // Catch-up sync if backgrounded for > 2 minutes
        scope.launch {
            val backgroundedAt = lastBackgroundedAt
            if (backgroundedAt != null) {
                val away = Clock.System.now() - backgroundedAt
                if (away > CATCHUP_THRESHOLD) {
                    log(TAG, INFO) { "Away for ${away.inWholeSeconds}s, running catch-up sync" }
                    syncExecutor.execute("ForegroundCatchUp")
                } else {
                    log(TAG) { "Away for ${away.inWholeSeconds}s, skipping catch-up" }
                }
            }
            lastBackgroundedAt = null
        }

        // Cancel any lingering collector (e.g. still draining via NonCancellable)
        eventCollectorJob?.cancel()
        eventCollectorJob = null

        // Start collecting sync events with channel-based batching
        eventCollectorJob = scope.launch {
            val eventChannel = Channel<SyncEvent.ModuleChanged>(
                capacity = 256,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

            // Producer: collect sync events, filter to UPDATED, send to channel
            val producerJob = launch {
                syncManager.syncEvents.collect { event ->
                    when (event) {
                        is SyncEvent.ModuleChanged -> when (event.action) {
                            SyncEvent.ModuleChanged.Action.UPDATED -> {
                                log(TAG, VERBOSE) { "Queued: ${event.moduleId.logLabel} on ${event.connectorId.logLabel}" }
                                eventChannel.send(event)
                            }

                            SyncEvent.ModuleChanged.Action.DELETED -> {
                                log(TAG, INFO) { "Module deleted: ${event.moduleId.logLabel}" }
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

                    // Once we have an event, protect the entire batch+sync from cancellation
                    withContext(NonCancellable) {
                        val pending = mutableMapOf<ConnectorId, PendingSync>()
                        pending.getOrPut(first.connectorId) { PendingSync() }.add(first)

                        // Drain events arriving within the debounce window
                        withTimeoutOrNull(CLIENT_DEBOUNCE) {
                            while (true) {
                                val event = eventChannel.receive()
                                pending.getOrPut(event.connectorId) { PendingSync() }.add(event)
                            }
                        }

                        withTimeoutOrNull(SYNC_COMPLETION_TIMEOUT) {
                            syncPendingModules(pending)
                        } ?: log(TAG, WARN) { "In-flight sync timed out after $SYNC_COMPLETION_TIMEOUT" }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    // Stop producer first so no more events arrive during drain
                    producerJob.cancel()
                    producerJob.join()

                    // Flush remaining events on cancellation
                    val remaining = mutableMapOf<ConnectorId, PendingSync>()
                    while (true) {
                        val event = eventChannel.tryReceive().getOrNull() ?: break
                        remaining.getOrPut(event.connectorId) { PendingSync() }.add(event)
                    }
                    if (remaining.isNotEmpty()) {
                        log(TAG, INFO) { "Flushing ${remaining.values.sumOf { it.modules.size }} pending events on shutdown" }
                        withTimeoutOrNull(SYNC_COMPLETION_TIMEOUT) {
                            syncPendingModules(remaining)
                        } ?: log(TAG, WARN) { "Flush sync timed out after $SYNC_COMPLETION_TIMEOUT" }
                    }
                }
            }
        }
    }

    private data class PendingSync(
        val modules: MutableSet<ModuleId> = mutableSetOf(),
        val devices: MutableSet<DeviceId> = mutableSetOf(),
    ) {
        fun add(event: SyncEvent.ModuleChanged) {
            modules.add(event.moduleId)
            devices.add(event.deviceId)
        }
    }

    private suspend fun syncPendingModules(pending: Map<ConnectorId, PendingSync>) {
        pending.forEach { (connectorId, sync) ->
            log(TAG) { "Batched sync: ${sync.modules.size} modules, ${sync.devices.size} devices on ${connectorId.logLabel}" }
            try {
                syncManager.sync(
                    connectorId,
                    SyncOptions(
                        stats = false,
                        readData = true,
                        writeData = false,
                        moduleFilter = sync.modules.toSet(),
                        deviceFilter = sync.devices.toSet(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Batched sync failed: ${e.asLog()}" }
            }
        }
    }

    private fun onBackground() {
        log(TAG, INFO) { "onBackground()" }
        lastBackgroundedAt = Clock.System.now()

        // Stop collecting events — cancels upstream WebSocket/polling via WhileSubscribed
        eventCollectorJob?.cancel()
        eventCollectorJob = null
    }

    companion object {
        private val CLIENT_DEBOUNCE = 500.milliseconds
        private val BACKGROUND_DEBOUNCE = 5.seconds
        private val SYNC_COMPLETION_TIMEOUT = 30.seconds
        private val CATCHUP_THRESHOLD = 2.minutes
        private val TAG = logTag("Sync", "Foreground", "Control")
    }
}
