package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.worker.SyncWorkerControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncOrchestrator @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val foregroundSyncControl: ForegroundSyncControl,
    private val syncWorkerControl: SyncWorkerControl,
    private val syncManager: SyncManager,
) {

    data class State(
        val quickSync: QuickSyncState,
        val backgroundSync: BackgroundSyncState,
    )

    data class QuickSyncState(
        val isActive: Boolean,
        val connectorModes: Map<ConnectorId, SyncConnector.EventMode>,
    )

    data class BackgroundSyncState(
        val defaultWorker: WorkerInfo,
        val chargingWorker: WorkerInfo,
    ) {
        data class WorkerInfo(
            val isEnabled: Boolean,
            val isRunning: Boolean,
            val isBlocked: Boolean,
            val nextRunAt: Instant?,
        )
    }

    private val connectorModes: Flow<Map<ConnectorId, SyncConnector.EventMode>> = syncManager.connectors
        .flatMapLatest { connectors ->
            if (connectors.isEmpty()) {
                flowOf(emptyMap())
            } else {
                kotlinx.coroutines.flow.combine(
                    connectors.map { c -> c.syncEventMode.map { mode -> c.identifier to mode } }
                ) { pairs -> pairs.toMap().filterValues { it != SyncConnector.EventMode.NONE } }
            }
        }

    val state: Flow<State> = combine(
        foregroundSyncControl.isActive,
        connectorModes,
        syncWorkerControl.workerState,
    ) { quickSyncActive, modes, workerState ->
        State(
            quickSync = QuickSyncState(isActive = quickSyncActive, connectorModes = modes),
            backgroundSync = BackgroundSyncState(
                defaultWorker = workerState.defaultWorker.toOrchestratorInfo(),
                chargingWorker = workerState.chargingWorker.toOrchestratorInfo(),
            ),
        )
    }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "state" }
        .shareLatest(scope)

    fun start() {
        log(TAG) { "start()" }
        syncWorkerControl.start()
        foregroundSyncControl.start()

        syncManager.pendingSyncTrigger
            .onEach {
                log(TAG) { "pendingSyncTrigger: syncing pending writes" }
                try {
                    syncManager.sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "pendingSyncTrigger: sync failed: ${e.asLog()}" }
                    syncManager.requestSync()
                }
            }
            .launchIn(scope)
    }

    companion object {
        private val TAG = logTag("App", "Sync", "Orchestrator")

        private fun SyncWorkerControl.WorkerState.WorkerInfo.toOrchestratorInfo() = BackgroundSyncState.WorkerInfo(
            isEnabled = isEnabled,
            isRunning = isRunning,
            isBlocked = isBlocked,
            nextRunAt = nextRunAt,
        )
    }
}
