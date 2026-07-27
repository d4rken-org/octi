package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.cache.SyncCache
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

/**
 * Single-flight behaviour of [SyncManager.sync].
 *
 * The connectors here are backed by a real [ConnectorProcessor] rather than a mock: a fake that
 * simply returns would pass even if supersession never reached the connector, and the ordering
 * guarantee ("the superseded command is terminal before its replacement starts") only exists
 * because the processor is a serial actor.
 */
class SyncManagerSingleFlightTest : BaseTest() {

    private val connectorId1 = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc1")
    private val connectorId2 = ConnectorId(type = ConnectorType.GDRIVE, subtype = "test", account = "acc2")
    private val deviceId = DeviceId("device-1")
    private val powerModuleId = ModuleId("eu.darken.octi.module.core.power")

    private lateinit var syncSettings: SyncSettings
    private lateinit var pauseStatesValue: MutableStateFlow<Set<ConnectorPauseState>>
    private lateinit var syncCache: SyncCache
    private lateinit var connectorHub: ConnectorHub
    private lateinit var connectorsFlow: MutableStateFlow<List<SyncConnector>>
    private lateinit var connectorSyncState: ConnectorSyncState

    /** Records what the executor saw, so tests can assert on accumulated options. */
    private val executions = mutableListOf<SyncOptions>()

    @BeforeEach
    fun setup() {
        executions.clear()
        pauseStatesValue = MutableStateFlow(emptySet())
        syncSettings = mockk(relaxed = true) {
            every { connectorPauseStates } returns pauseStatesValue
            every { pausedConnectorIds } returns pauseStatesValue.map { it.connectorIds }
            coEvery { isPaused(any()) } coAnswers {
                pauseStatesValue.value.reasonFor(firstArg<ConnectorId>()) != null
            }
            coEvery { migrateLegacyPauseStates() } coAnswers { }
        }
        every { syncSettings.deviceId } returns deviceId
        syncCache = mockk(relaxed = true)
        connectorSyncState = ConnectorSyncState()
        connectorsFlow = MutableStateFlow(emptyList())
        connectorHub = mockk(relaxed = true) {
            every { connectors } returns connectorsFlow
        }
    }

    /** A connector driven by a real [ConnectorProcessor], so cancel()/coalescing are the real thing. */
    private inner class FakeConnector(
        override val identifier: ConnectorId,
        processorScope: CoroutineScope,
        onOther: suspend (ConnectorCommand) -> Unit = { },
        /**
         * When set, the FIRST [await] parks non-cancellably until it completes — modelling a
         * manager-side wait that does not unwind the moment its run is superseded, which is what
         * blocking connector work (GDrive's `.execute()`) produces in practice.
         */
        private val awaitHold: CompletableDeferred<Unit>? = null,
        executor: suspend (SyncOptions) -> Unit,
    ) : SyncConnector {
        private val processor = ConnectorProcessor(
            connectorId = identifier,
            syncSettings = syncSettings,
            timeouts = ConnectorProcessor.Timeouts.UNBOUNDED,
            executor = { command ->
                if (command is ConnectorCommand.Sync) {
                    executions += command.options
                    executor(command.options)
                } else {
                    onOther(command)
                }
            },
        )

        val job: Job = processor.start(processorScope)

        override val accountLabel: String = "fake"
        override val capabilities = ConnectorCapabilities(deviceRemovalPolicy = DeviceRemovalPolicy.REMOVE_LOCAL_ONLY)
        override val state = flowOf<SyncConnectorState>()
        override val data = flowOf<SyncRead?>(null)
        override val operations = processor.operations
        override val completions = processor.completions
        override fun submit(command: ConnectorCommand) = processor.submit(command)
        override fun submitExclusive(command: ConnectorCommand) = processor.submitExclusive(command)
        private var awaitHoldUsed = false

        override suspend fun await(id: OperationId): ConnectorOperation.Terminal {
            val hold = awaitHold
            if (hold == null || awaitHoldUsed) return processor.await(id)
            awaitHoldUsed = true
            val terminal = processor.await(id)
            withContext(NonCancellable) { hold.await() }
            return terminal
        }

        override fun cancel(id: OperationId) = processor.cancel(id)
        override fun dismiss(id: OperationId) = processor.dismiss(id)
    }

    private fun TestScope.createSyncManager(timeSource: TestTimeSource? = null): Pair<SyncManager, Job> {
        val job = Job()
        val sm = SyncManager(
            scope = this + job,
            dispatcherProvider = mockk(relaxed = true) {
                every { Default } returns coroutineContext[kotlinx.coroutines.CoroutineDispatcher.Key]!!
            },
            syncSettings = syncSettings,
            syncCache = syncCache,
            connectorHubs = setOf(connectorHub),
            connectorSyncState = connectorSyncState,
        )
        if (timeSource != null) sm.timeSource = timeSource
        return sm to job
    }

    @Nested
    inner class `coalescing` {

        @Test
        fun `a request arriving mid-run is accumulated, not lost`() = runTest2 {
            val gate = CompletableDeferred<Unit>()
            val processorJob = SupervisorJob()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) gate.await() },
            )
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val first = launch { sm.sync(SyncOptions(stats = false, readData = true, writeData = false)) }
            advanceUntilIdle()

            // Arrives while the first batch is in flight. Its scope must survive into the re-run —
            // a write request absorbed by a read-only one would silently drop the write.
            val second = launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }
            advanceUntilIdle()

            gate.complete(Unit)
            advanceUntilIdle()
            first.join()
            second.join()

            executions shouldHaveSize 2
            executions[0].readData shouldBe true
            executions[0].writeData shouldBe false
            executions[1].writeData shouldBe true

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `requests arriving in the final-check window are accumulated, not lost`() = runTest2 {
            // The run decides "nothing pending, I'm done" and clears itself under the same lock a
            // new request takes, so a request can never fall between the two.
            val processorJob = SupervisorJob()
            connectorsFlow.value = listOf(FakeConnector(connectorId1, this + processorJob) { })
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val waiters = (1..20).map {
                launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }
            }
            advanceUntilIdle()
            waiters.forEach { it.join() }

            // Every caller's request ran (each returned), while the runs stayed coalesced.
            executions.size shouldBeLessThan 20
            executions.forEach { it.writeData shouldBe true }

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a folded request still waits for its own work to run`() = runTest2 {
            val gate = CompletableDeferred<Unit>()
            val processorJob = SupervisorJob()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) gate.await() },
            )
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            launch { sm.sync() }
            advanceUntilIdle()

            var secondReturned = false
            val second = launch {
                sm.sync(SyncOptions(stats = false, readData = false, writeData = true))
                secondReturned = true
            }
            advanceUntilIdle()
            secondReturned shouldBe false

            gate.complete(Unit)
            advanceUntilIdle()
            second.join()
            secondReturned shouldBe true

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `supersession` {

        @Test
        fun `a hanging connector does not block later syncs forever`() = runTest2 {
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val hang = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) hang.await() },
            )
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            val stuck = launch { sm.sync() }
            advanceUntilIdle()
            executions shouldHaveSize 1

            // Past the staleness threshold the wedged run is taken over instead of the new request
            // being dropped on the floor.
            timeSource += SyncManager.SYNC_STALE_AFTER + 1.minutes
            val later = launch { sm.sync() }
            advanceUntilIdle()
            later.join()

            executions shouldHaveSize 2
            stuck.cancel()

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a request within the staleness window coalesces instead of superseding`() = runTest2 {
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val gate = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) gate.await() },
            )
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            val first = launch { sm.sync() }
            advanceUntilIdle()

            timeSource += SyncManager.SYNC_STALE_AFTER - 1.minutes
            val second = launch { sm.sync() }
            advanceUntilIdle()

            // Still only the original execution — the second request waits for the run instead of
            // tearing it down.
            executions shouldHaveSize 1

            gate.complete(Unit)
            advanceUntilIdle()
            first.join()
            second.join()
            executions shouldHaveSize 2

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `the superseded command is terminal before its replacement starts`() = runTest2 {
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val events = mutableListOf<String>()
            val hang = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) {
                    val index = executions.size
                    events += "start-$index"
                    try {
                        if (index == 1) hang.await()
                    } finally {
                        events += "end-$index"
                    }
                },
            )
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            val stuck = launch { sm.sync() }
            advanceUntilIdle()

            timeSource += SyncManager.SYNC_STALE_AFTER + 1.minutes
            val replacement = launch { sm.sync() }
            advanceUntilIdle()
            replacement.join()

            // Not merely "the manager's waiter was cancelled": the connector's own command reached
            // terminal state first, and only then did the replacement begin.
            events shouldBe listOf("start-1", "end-1", "start-2", "end-2")
            stuck.cancel()

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a superseded batch's request is carried into the replacement run`() = runTest2 {
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val hang = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) hang.await() },
            )
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            // A write-only request gets stuck...
            val stuck = launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }
            advanceUntilIdle()

            // ...and a read-only one supersedes it. The write must not be dropped.
            timeSource += SyncManager.SYNC_STALE_AFTER + 1.minutes
            val later = launch { sm.sync(SyncOptions(stats = false, readData = true, writeData = false)) }
            advanceUntilIdle()
            later.join()
            stuck.join()

            executions.last().readData shouldBe true
            executions.last().writeData shouldBe true

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `caller cancellation` {

        @Test
        fun `cancelling the last caller cancels the connector operation`() = runTest2 {
            val processorJob = SupervisorJob()
            val events = mutableListOf<String>()
            val hang = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) {
                    events += "start"
                    try {
                        hang.await()
                    } finally {
                        events += "end"
                    }
                },
            )
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val caller = launch { sm.sync() }
            advanceUntilIdle()
            events shouldBe listOf("start")

            // The run lives on @AppScope: without withdrawing the request, cancelling the caller
            // would leave the connector command hitting the network for nobody.
            caller.cancel()
            advanceUntilIdle()

            events shouldBe listOf("start", "end")
            executions shouldHaveSize 1

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `cancelling one of two coalesced callers leaves the work running`() = runTest2 {
            val processorJob = SupervisorJob()
            val events = mutableListOf<String>()
            val hang = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) {
                    events += "start"
                    try {
                        hang.await()
                    } finally {
                        events += "end"
                    }
                },
            )
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            // Both records land before the run claims a batch, so they own the same iteration.
            val first = launch { sm.sync(SyncOptions(stats = false, readData = true, writeData = false)) }
            val second = launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }
            advanceUntilIdle()
            events shouldBe listOf("start")

            first.cancel()
            advanceUntilIdle()

            // Still running for the remaining owner — options already merged into the executing
            // command cannot be subtracted mid-flight.
            events shouldBe listOf("start")

            hang.complete(Unit)
            advanceUntilIdle()
            second.join()
            events shouldBe listOf("start", "end")
            executions shouldHaveSize 1
            executions.single().readData shouldBe true
            executions.single().writeData shouldBe true

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a cancelled caller does not take another caller's queued request with it`() = runTest2 {
            val processorJob = SupervisorJob()
            val gate = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) gate.await() },
            )
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val stuck = launch { sm.sync() }
            advanceUntilIdle()

            // Arrives after the batch was claimed, so it waits for the next iteration.
            val later = launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }
            advanceUntilIdle()

            stuck.cancel()
            advanceUntilIdle()
            later.join()

            executions.last().writeData shouldBe true

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a caller cancelled after its run was superseded leaves nothing behind`() = runTest2 {
            // Run A is superseded, so activeRun is already B, but A's cancellation has not landed:
            // it still owns the batch holding the stuck caller's record. Resolving the owning batch
            // through activeRun misses that record entirely, and A's finally then folds it back into
            // pending — so the withdrawn options run again for a caller that is long gone.
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val releaseA = CompletableDeferred<Unit>()
            val gateB = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(
                    connectorId1,
                    this + processorJob,
                    awaitHold = releaseA,
                ) { if (executions.size == 2) gateB.await() },
            )
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            val optionsA = SyncOptions(
                stats = false,
                readData = true,
                writeData = false,
                moduleFilter = setOf(powerModuleId),
            )
            val stuck = launch { sm.sync(optionsA) }
            advanceUntilIdle()
            executions shouldHaveSize 1

            // Past the staleness threshold: B takes over while A is still parked in its wait.
            timeSource += SyncManager.SYNC_STALE_AFTER + 1.minutes
            val replacement = launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }
            advanceUntilIdle()
            executions shouldHaveSize 2

            // The only owner of A's batch walks away inside that window.
            stuck.cancel()
            advanceUntilIdle()

            releaseA.complete(Unit)
            gateB.complete(Unit)
            advanceUntilIdle()
            replacement.join()

            // A's options were withdrawn: never requeued, never executed a second time.
            executions shouldHaveSize 2
            executions.count { it.moduleFilter == setOf(powerModuleId) } shouldBe 1

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a direct per-connector sync survives the manager superseding its own run`() = runTest2 {
            // Both syncs queue behind a destructive command, so the manager's Sync used to fold
            // into the direct caller's queued op and receive that shared id. Superseding then
            // cancelled the direct caller along with it, dropping its module filter.
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val blocker = CompletableDeferred<Unit>()
            val connector = FakeConnector(
                connectorId1,
                this + processorJob,
                onOther = { blocker.await() },
                executor = { },
            )
            connectorsFlow.value = listOf(connector)
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            // Occupies the actor: Reset is destructive, so supersede cannot cancel it either.
            connector.submit(ConnectorCommand.Reset)
            advanceUntilIdle()

            var directOutcome: Result<Unit>? = null
            val direct = launch {
                directOutcome = runCatching {
                    sm.sync(
                        connectorId1,
                        SyncOptions(
                            stats = false,
                            readData = true,
                            writeData = false,
                            moduleFilter = setOf(powerModuleId),
                        ),
                    )
                }
            }
            advanceUntilIdle()

            val stuck = launch { sm.sync() }
            advanceUntilIdle()

            timeSource += SyncManager.SYNC_STALE_AFTER + 1.minutes
            val replacement = launch { sm.sync() }
            advanceUntilIdle()

            blocker.complete(Unit)
            advanceUntilIdle()
            direct.join()

            // The direct caller's own op ran, with its filter intact.
            directOutcome!!.isSuccess shouldBe true
            executions.count { it.moduleFilter == setOf(powerModuleId) } shouldBe 1

            stuck.cancel()
            replacement.cancel()
            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `connector resolution` {

        @Test
        fun `connectors that never emit do not wedge the run`() = runTest2 {
            // A hub whose connector flow never emits used to leave the critical section held for
            // the process lifetime.
            val neverEmits = mockk<ConnectorHub>(relaxed = true) {
                every { connectors } returns MutableSharedFlow()
            }
            connectorHub = neverEmits
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val first = launch { sm.sync() }
            advanceUntilIdle()
            first.join()

            // And the manager still accepts work afterwards.
            val second = launch { sm.sync() }
            advanceUntilIdle()
            second.join()

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `progress` {

        @Test
        fun `in-flight connectors are exposed while a sync runs`() = runTest2 {
            val processorJob = SupervisorJob()
            val gate = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) gate.await() },
                FakeConnector(connectorId2, this + processorJob) { },
            )
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            val running = launch { sm.sync() }
            advanceUntilIdle()

            sm.activeConnectorSyncs.value shouldBe setOf(connectorId1)
            sm.lastConnectorSyncOutcomes.value[connectorId2] shouldBe SyncManager.ConnectorSyncOutcome.Success

            gate.complete(Unit)
            advanceUntilIdle()
            running.join()
            sm.activeConnectorSyncs.value shouldBe emptySet()

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `payloads` {

        @Test
        fun `accumulated writes still carry the changed module payload`() = runTest2 {
            val processorJob = SupervisorJob()
            connectorsFlow.value = listOf(FakeConnector(connectorId1, this + processorJob) { })
            val (sm, job) = createSyncManager()
            advanceUntilIdle()

            sm.updatePayload(
                mockk<SyncWrite.Device.Module> {
                    every { moduleId } returns powerModuleId
                    every { payload } returns okio.ByteString.of(1, 2, 3)
                }
            )

            launch { sm.sync(SyncOptions(stats = false, readData = false, writeData = true)) }.join()
            advanceUntilIdle()

            executions.first().writePayload.single().module.moduleId shouldBe powerModuleId

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `timing` {

        @Test
        fun `staleness uses a monotonic source, unaffected by wall-clock jumps`() = runTest2 {
            // Virtual time advancing (which a wall-clock-based check would follow) must not make a
            // healthy run look stale — only the injected monotonic source decides.
            val timeSource = TestTimeSource()
            val processorJob = SupervisorJob()
            val gate = CompletableDeferred<Unit>()
            connectorsFlow.value = listOf(
                FakeConnector(connectorId1, this + processorJob) { if (executions.size == 1) gate.await() },
            )
            val (sm, job) = createSyncManager(timeSource)
            advanceUntilIdle()

            val first = launch { sm.sync() }
            advanceUntilIdle()

            advanceTimeBy(60.minutes)
            val second = launch { sm.sync() }
            advanceUntilIdle()

            executions shouldHaveSize 1

            gate.complete(Unit)
            advanceUntilIdle()
            first.join()
            second.join()

            processorJob.cancel()
            job.cancel()
            advanceUntilIdle()
        }
    }
}
