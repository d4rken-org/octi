package eu.darken.octi.sync.core

import eu.darken.octi.sync.core.worker.SyncWorkerControl
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Instant

class SyncOrchestratorTest : BaseTest() {

    private val foregroundSyncControl = mockk<ForegroundSyncControl>(relaxed = true)
    private val syncWorkerControl = mockk<SyncWorkerControl>(relaxed = true)
    private val syncManager = mockk<SyncManager>(relaxed = true)

    private val isActiveFlow = MutableStateFlow(false)
    private val connectorsFlow = MutableStateFlow<List<SyncConnector>>(emptyList())
    private val workerStateFlow = MutableStateFlow(
        SyncWorkerControl.WorkerState(
            defaultWorker = SyncWorkerControl.WorkerState.WorkerInfo(
                isEnabled = false,
                isRunning = false,
                isBlocked = false,
                nextRunAt = null,
            ),
            chargingWorker = SyncWorkerControl.WorkerState.WorkerInfo(
                isEnabled = false,
                isRunning = false,
                isBlocked = false,
                nextRunAt = null,
            ),
        )
    )

    @BeforeEach
    fun setup() {
        every { foregroundSyncControl.isActive } returns isActiveFlow
        every { syncManager.connectors } returns connectorsFlow
        every { syncWorkerControl.workerState } returns workerStateFlow
    }

    private fun createOrchestrator(scope: CoroutineScope) = SyncOrchestrator(
        scope = scope,
        foregroundSyncControl = foregroundSyncControl,
        syncWorkerControl = syncWorkerControl,
        syncManager = syncManager,
    )

    private fun mockConnector(type: String): SyncConnector = mockk {
        every { identifier } returns ConnectorId(type = type, subtype = "test", account = "test")
    }

    @Nested
    inner class `quick sync state` {
        @Test
        fun `active with kserver shows LIVE mode`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(mockConnector("kserver"))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe true
            state.quickSync.connectorModes shouldBe mapOf("kserver" to SyncOrchestrator.QuickSyncState.Mode.LIVE)
        }

        @Test
        fun `active with gdrive shows POLLING mode`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(mockConnector("gdrive"))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe true
            state.quickSync.connectorModes shouldBe mapOf("gdrive" to SyncOrchestrator.QuickSyncState.Mode.POLLING)
        }

        @Test
        fun `active with both connectors shows mixed modes`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(mockConnector("kserver"), mockConnector("gdrive"))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe true
            state.quickSync.connectorModes shouldBe mapOf(
                "kserver" to SyncOrchestrator.QuickSyncState.Mode.LIVE,
                "gdrive" to SyncOrchestrator.QuickSyncState.Mode.POLLING,
            )
        }

        @Test
        fun `inactive shows empty modes`() = runTest2(autoCancel = true) {
            isActiveFlow.value = false
            connectorsFlow.value = listOf(mockConnector("kserver"))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe false
            state.quickSync.connectorModes shouldBe emptyMap()
        }
    }

    @Nested
    inner class `background sync state` {
        @Test
        fun `propagates default worker running state`() = runTest2(autoCancel = true) {
            connectorsFlow.value = listOf(mockConnector("kserver"))
            workerStateFlow.value = workerStateFlow.value.copy(
                defaultWorker = SyncWorkerControl.WorkerState.WorkerInfo(
                    isEnabled = true,
                    isRunning = true,
                    isBlocked = false,
                    nextRunAt = null,
                ),
            )

            val state = createOrchestrator(this).state.first()

            state.backgroundSync.defaultWorker.isRunning shouldBe true
            state.backgroundSync.defaultWorker.isEnabled shouldBe true
        }

        @Test
        fun `propagates next run time`() = runTest2(autoCancel = true) {
            val nextRun = Instant.now().plusSeconds(2700)
            connectorsFlow.value = listOf(mockConnector("kserver"))
            workerStateFlow.value = workerStateFlow.value.copy(
                defaultWorker = SyncWorkerControl.WorkerState.WorkerInfo(
                    isEnabled = true,
                    isRunning = false,
                    isBlocked = false,
                    nextRunAt = nextRun,
                ),
            )

            val state = createOrchestrator(this).state.first()

            state.backgroundSync.defaultWorker.nextRunAt shouldBe nextRun
        }
    }

    @Nested
    inner class `lifecycle` {
        @Test
        fun `start delegates to worker control and foreground control`() = runTest2(autoCancel = true) {
            val orchestrator = createOrchestrator(this)
            orchestrator.start()

            verify { syncWorkerControl.start() }
            verify { foregroundSyncControl.start() }
        }
    }
}
