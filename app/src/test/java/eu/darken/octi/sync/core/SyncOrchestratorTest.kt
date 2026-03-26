package eu.darken.octi.sync.core

import eu.darken.octi.sync.core.ConnectorType
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

    private fun mockConnector(
        type: ConnectorType,
        mode: SyncConnector.EventMode = SyncConnector.EventMode.NONE,
    ): SyncConnector = mockk {
        every { identifier } returns ConnectorId(type = type, subtype = "test", account = "test")
        every { syncEventMode } returns MutableStateFlow(mode)
    }

    @Nested
    inner class `quick sync state` {
        @Test
        fun `octiserver with LIVE mode reported by connector`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(mockConnector(ConnectorType.OCTISERVER, SyncConnector.EventMode.LIVE))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe true
            state.quickSync.connectorModes.values.toList() shouldBe listOf(SyncConnector.EventMode.LIVE)
        }

        @Test
        fun `gdrive with POLLING mode reported by connector`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(mockConnector(ConnectorType.GDRIVE, SyncConnector.EventMode.POLLING))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe true
            state.quickSync.connectorModes.values.toList() shouldBe listOf(SyncConnector.EventMode.POLLING)
        }

        @Test
        fun `mixed modes from multiple connectors`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(
                mockConnector(ConnectorType.OCTISERVER, SyncConnector.EventMode.LIVE),
                mockConnector(ConnectorType.GDRIVE, SyncConnector.EventMode.POLLING),
            )

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe true
            state.quickSync.connectorModes.size shouldBe 2
        }

        @Test
        fun `connector with NONE mode is filtered out`() = runTest2(autoCancel = true) {
            isActiveFlow.value = true
            connectorsFlow.value = listOf(mockConnector(ConnectorType.OCTISERVER, SyncConnector.EventMode.NONE))

            val state = createOrchestrator(this).state.first()

            state.quickSync.connectorModes shouldBe emptyMap()
        }

        @Test
        fun `inactive but connector reports LIVE still shows mode`() = runTest2(autoCancel = true) {
            isActiveFlow.value = false
            connectorsFlow.value = listOf(mockConnector(ConnectorType.OCTISERVER, SyncConnector.EventMode.LIVE))

            val state = createOrchestrator(this).state.first()

            state.quickSync.isActive shouldBe false
            state.quickSync.connectorModes.values.toList() shouldBe listOf(SyncConnector.EventMode.LIVE)
        }
    }

    @Nested
    inner class `background sync state` {
        @Test
        fun `propagates default worker running state`() = runTest2(autoCancel = true) {
            connectorsFlow.value = listOf(mockConnector(ConnectorType.OCTISERVER))
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
            connectorsFlow.value = listOf(mockConnector(ConnectorType.OCTISERVER))
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
