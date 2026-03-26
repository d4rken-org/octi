package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.worker.SyncWorkerControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

    val state: Flow<State> = combine(
        foregroundSyncControl.isActive,
        syncManager.connectors,
        syncWorkerControl.workerState,
    ) { quickSyncActive, connectors, workerState ->
        val modes = connectors
            .associate { it.identifier to it.syncEventMode.value }
            .filterValues { it != SyncConnector.EventMode.NONE }
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
