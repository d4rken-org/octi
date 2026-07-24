package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.errors.ConnectorPausedException
import eu.darken.octi.sync.core.errors.ConnectorStoppedException
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class ConnectorProcessorTest : BaseTest() {

    private val connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "test", account = "acc")
    private lateinit var syncSettings: SyncSettings
    private lateinit var pauseStatesValue: MutableStateFlow<Set<ConnectorPauseState>>

    @BeforeEach
    fun setup() {
        pauseStatesValue = MutableStateFlow(emptySet())
        syncSettings = mockk(relaxed = true) {
            every { connectorPauseStates } returns pauseStatesValue
            coEvery { isPaused(any()) } coAnswers {
                pauseStatesValue.value.reasonFor(firstArg<ConnectorId>()) != null
            }
        }
    }

    private fun TestScope.buildProcessor(
        retention: Int = 20,
        executor: suspend (ConnectorCommand) -> Unit = { },
    ): Pair<ConnectorProcessor, Job> {
        val job = SupervisorJob()
        val processor = ConnectorProcessor(
            connectorId = connectorId,
            syncSettings = syncSettings,
            displayRetention = retention,
            executor = executor,
        )
        processor.start(this + job)
        return processor to job
    }

    @Test
    fun `submitted commands are executed in submission order`() = runTest2 {
        val executed = mutableListOf<ConnectorCommand>()
        val (proc, job) = buildProcessor(executor = { cmd -> executed += cmd })

        proc.submit(ConnectorCommand.Sync())
        proc.submit(ConnectorCommand.DeleteDevice(DeviceId("a")))
        proc.submit(ConnectorCommand.Reset)
        advanceUntilIdle()

        executed.shouldHaveSize(3)
        executed[0].shouldBeInstanceOf<ConnectorCommand.Sync>()
        executed[1].shouldBeInstanceOf<ConnectorCommand.DeleteDevice>()
        executed[2].shouldBeInstanceOf<ConnectorCommand.Reset>()

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `pause command blocks subsequent non-pause-resume commands`() = runTest2 {
        val executed = mutableListOf<ConnectorCommand>()
        val (proc, job) = buildProcessor(executor = { cmd ->
            // Pause/Resume handlers are inline here (not via SyncSettings) so the guard reads it.
            when (cmd) {
                is ConnectorCommand.Pause -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet() + ConnectorPauseState(connectorId, cmd.reason)
                ConnectorCommand.Resume -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet()
                else -> executed += cmd
            }
        })

        proc.submit(ConnectorCommand.Pause())
        val syncId = proc.submit(ConnectorCommand.Sync())
        advanceUntilIdle()

        executed.shouldHaveSize(0)
        val terminal = proc.await(syncId)
        terminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
        terminal.error.shouldBeInstanceOf<ConnectorPausedException>()

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `resume side-effect enqueues a sync`() = runTest2 {
        val executed = mutableListOf<ConnectorCommand>()
        val (proc, job) = buildProcessor(executor = { cmd ->
            executed += cmd
            when (cmd) {
                is ConnectorCommand.Pause -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet() + ConnectorPauseState(connectorId, cmd.reason)
                ConnectorCommand.Resume -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet()
                else -> Unit
            }
        })
        pauseStatesValue.value = setOf(ConnectorPauseState(connectorId, ConnectorPauseReason.Manual))

        proc.submit(ConnectorCommand.Resume)
        advanceUntilIdle()

        // Resume ran, then the processor's side-effect enqueued a Sync which also ran.
        executed.map { it::class.simpleName } shouldContain "Resume"
        executed.any { it is ConnectorCommand.Sync } shouldBe true

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `resume then pause with awaited ordering — sync lands between them`() = runTest2 {
        val executed = mutableListOf<ConnectorCommand>()
        val (proc, job) = buildProcessor(executor = { cmd ->
            executed += cmd
            when (cmd) {
                is ConnectorCommand.Pause -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet() + ConnectorPauseState(connectorId, cmd.reason)
                ConnectorCommand.Resume -> pauseStatesValue.value = pauseStatesValue.value
                    .filterNot { it.connectorId == connectorId }
                    .toSet()
                else -> Unit
            }
        })
        pauseStatesValue.value = setOf(ConnectorPauseState(connectorId, ConnectorPauseReason.Manual))

        // User first awaits Resume (as happens via togglePause → execute(Resume)) — by the time
        // Resume's terminal resolves, the side-effect Sync has already been submitted into the
        // inbox. Any subsequent user submit (Pause) is strictly ordered after that Sync.
        val resumeId = proc.submit(ConnectorCommand.Resume)
        advanceUntilIdle()
        proc.await(resumeId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()

        proc.submit(ConnectorCommand.Pause())
        advanceUntilIdle()

        // Executed order: Resume → Sync → Pause.
        val names = executed.map { it::class.simpleName }
        names shouldContain "Resume"
        names shouldContain "Sync"
        names shouldContain "Pause"
        names.indexOf("Sync") shouldBe names.indexOf("Resume") + 1
        names.indexOf("Pause") shouldBe names.indexOf("Sync") + 1

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `per-command CancellationException does not stop the processor`() = runTest2 {
        val executed = mutableListOf<ConnectorCommand>()
        val (proc, job) = buildProcessor(executor = { cmd ->
            if (cmd is ConnectorCommand.Sync) throw CancellationException("per-command cancel")
            executed += cmd
        })

        val failingId = proc.submit(ConnectorCommand.Sync())
        val nextId = proc.submit(ConnectorCommand.Reset)
        advanceUntilIdle()

        proc.await(failingId).shouldBeInstanceOf<ConnectorOperation.Failed>()
        proc.await(nextId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()
        executed.shouldHaveSize(1) // Reset

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `retention trims completed ops beyond limit`() = runTest2 {
        val (proc, job) = buildProcessor(retention = 3, executor = { })
        repeat(5) { proc.submit(ConnectorCommand.Sync()) }
        advanceUntilIdle()

        val ops = proc.operations.first()
        ops.filterIsInstance<ConnectorOperation.Terminal>().shouldHaveSize(3)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `completions flow emits every terminal exactly once`() = runTest2 {
        val (proc, job) = buildProcessor(executor = { })
        val collected = mutableListOf<ConnectorOperation.Terminal>()
        val collector = launch {
            proc.completions.collect { collected += it }
        }
        advanceUntilIdle() // let the collector actually subscribe before we submit

        proc.submit(ConnectorCommand.Sync())
        proc.submit(ConnectorCommand.Reset)
        advanceUntilIdle()

        collected.shouldHaveSize(2)

        collector.cancel()
        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `await resolves independent of retention — trimmed op still awaitable if still pending`() = runTest2 {
        val gate = CompletableDeferred<Unit>()
        val (proc, job) = buildProcessor(retention = 1, executor = { cmd ->
            if (cmd is ConnectorCommand.Sync) gate.await()
        })

        val firstId = proc.submit(ConnectorCommand.Sync())
        // Let it reach Processing state
        advanceUntilIdle()

        // Release the gate; the op completes and the pending map entry resolves.
        gate.complete(Unit)
        advanceUntilIdle()

        proc.await(firstId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `dismiss removes terminal entry from operations list`() = runTest2 {
        val (proc, job) = buildProcessor(executor = { })
        val id = proc.submit(ConnectorCommand.Sync())
        advanceUntilIdle()

        proc.operations.first().filterIsInstance<ConnectorOperation.Terminal>().shouldHaveSize(1)

        proc.dismiss(id)
        proc.operations.first().filterIsInstance<ConnectorOperation.Terminal>().shouldHaveSize(0)

        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `processor scope cancellation fails pending ops — waiters don't hang`() = runTest2 {
        val gate = CompletableDeferred<Unit>()
        val (proc, job) = buildProcessor(executor = { gate.await() })

        val id = proc.submit(ConnectorCommand.Sync())
        advanceUntilIdle()

        job.cancel()
        advanceUntilIdle()

        // The processor teardown should have completed the pending deferred as Failed.
        proc.await(id).shouldBeInstanceOf<ConnectorOperation.Failed>()
    }

    @Test
    fun `processor scope cancellation clears busy operations, not just waiters`() = runTest2 {
        val gate = CompletableDeferred<Unit>()
        val (proc, job) = buildProcessor(executor = { gate.await() })

        proc.submit(ConnectorCommand.Sync()) // becomes Processing (awaits gate)
        proc.submit(ConnectorCommand.Sync()) // stays Queued behind it
        advanceUntilIdle()

        // Busy: at least one Queued/Processing entry.
        proc.operations.first()
            .any { it is ConnectorOperation.Queued || it is ConnectorOperation.Processing } shouldBe true

        job.cancel()
        advanceUntilIdle()

        // After shutdown no non-terminal op remains, so isBusy would report false — the connector card
        // and its gated actions recover instead of staying stuck "busy".
        val ops = proc.operations.first()
        ops.none { it is ConnectorOperation.Queued || it is ConnectorOperation.Processing } shouldBe true
        ops.filterIsInstance<ConnectorOperation.Failed>().shouldHaveSize(2)
    }

    @Test
    fun `scope cancelled before the actor is dispatched still terminates submitted ops`() = runTest2 {
        val (proc, job) = buildProcessor(executor = { })
        // Cancel BEFORE advancing: the actor coroutine has been launched but not yet dispatched. With
        // CoroutineStart.DEFAULT the body (and its shutdown finally) would be skipped entirely, leaving
        // this op queued forever and await() hanging. ATOMIC start guarantees the finally runs.
        job.cancel()
        val id = proc.submit(ConnectorCommand.Sync())
        advanceUntilIdle()

        proc.await(id).shouldBeInstanceOf<ConnectorOperation.Terminal>()
    }

    @Test
    fun `submitting after shutdown fails fast instead of hanging`() = runTest2 {
        val (proc, job) = buildProcessor()
        advanceUntilIdle() // let the actor start and reach inbox.receive() before we tear it down
        job.cancel()
        advanceUntilIdle() // run cancellation + finally, which closes the inbox

        // A stale reference submitting after teardown must not enqueue onto a consumer-less channel;
        // with the inbox closed, submit completes the op as Failed instead of leaving it Queued forever.
        val id = proc.submit(ConnectorCommand.Sync())
        proc.await(id).shouldBeInstanceOf<ConnectorOperation.Failed>()
    }

    @Test
    fun `active waiters on more ops than retention all resolve at shutdown`() = runTest2 {
        val gate = CompletableDeferred<Unit>()
        // Retention smaller than the op count: after shutdown, `operations` keeps only 2 terminals. Real
        // callers (SyncConnector.execute) await immediately after submit, so model suspended waiters —
        // they resolve from their deferred and must never hang, independent of retention trimming.
        val (proc, job) = buildProcessor(retention = 2, executor = { gate.await() })

        val ids = (1..5).map { proc.submit(ConnectorCommand.Sync()) }
        val waiters = ids.map { id -> async { proc.await(id) } }
        advanceUntilIdle() // first becomes Processing on the gate, rest stay Queued; all waiters suspend

        job.cancel()
        advanceUntilIdle()

        proc.operations.first().filterIsInstance<ConnectorOperation.Terminal>().shouldHaveSize(2)
        waiters.forEach { it.await().shouldBeInstanceOf<ConnectorOperation.Failed>() }
    }

    @Test
    fun `shutdown fails still-queued ops with ConnectorStoppedException, not a CancellationException`() = runTest2 {
        val gate = CompletableDeferred<Unit>()
        val (proc, job) = buildProcessor(executor = { gate.await() })

        proc.submit(ConnectorCommand.Sync()) // becomes Processing, blocks on the gate
        val queuedId = proc.submit(ConnectorCommand.Sync()) // never pulled — stays Queued behind it
        advanceUntilIdle()

        job.cancel()
        advanceUntilIdle()

        // The still-queued op is failed by the shutdown path, whose error must NOT be a
        // CancellationException: ForegroundSyncControl's serial loop catches cancellation to honor
        // structured concurrency and would otherwise abort the remaining connectors in the batch.
        val terminal = proc.await(queuedId)
        terminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
        // ConnectorStoppedException is an IllegalStateException, not a CancellationException.
        terminal.error.shouldBeInstanceOf<ConnectorStoppedException>()
    }
}
