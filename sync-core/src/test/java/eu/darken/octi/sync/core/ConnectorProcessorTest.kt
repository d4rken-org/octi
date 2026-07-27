package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.errors.ConnectorCancelledException
import eu.darken.octi.sync.core.errors.ConnectorPausedException
import eu.darken.octi.sync.core.errors.ConnectorStoppedException
import eu.darken.octi.sync.core.errors.ConnectorTimeoutException
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    /**
     * Defaults to [ConnectorProcessor.Timeouts.UNBOUNDED]: most tests here gate an executor on a
     * deferred and then call [advanceUntilIdle], which would otherwise run the virtual clock into
     * the production per-command bound and turn every gated op into a timeout. The bounds
     * themselves are covered by the dedicated tests below, which pass explicit short values.
     */
    private fun TestScope.buildProcessor(
        retention: Int = 20,
        timeouts: ConnectorProcessor.Timeouts = ConnectorProcessor.Timeouts.UNBOUNDED,
        executor: suspend (ConnectorCommand) -> Unit = { },
    ): Pair<ConnectorProcessor, Job> {
        val job = SupervisorJob()
        val processor = ConnectorProcessor(
            connectorId = connectorId,
            syncSettings = syncSettings,
            displayRetention = retention,
            timeouts = timeouts,
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
        // Distinct commands: queued Syncs coalesce into one op, which would defeat the point here.
        repeat(5) { proc.submit(ConnectorCommand.DeleteDevice(DeviceId("device-$it"))) }
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
        proc.submit(ConnectorCommand.DeleteDevice(DeviceId("a"))) // stays Queued behind it
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

        // Distinct commands: queued Syncs would coalesce into a single op.
        val ids = (1..5).map { proc.submit(ConnectorCommand.DeleteDevice(DeviceId("device-$it"))) }
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
        // Not a Sync: a queued Sync would coalesce into the one already in flight.
        val queuedId = proc.submit(ConnectorCommand.DeleteDevice(DeviceId("a"))) // stays Queued behind it
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

    @Nested
    inner class `per-command timeouts` {

        private val shortTimeouts = ConnectorProcessor.Timeouts(
            sync = 10.seconds,
            destructive = 60.seconds,
            local = 10.seconds,
        )

        @Test
        fun `executor that never returns fails with ConnectorTimeoutException`() = runTest2 {
            val (proc, job) = buildProcessor(
                timeouts = shortTimeouts,
                executor = { awaitCancellation() },
            )

            val id = proc.submit(ConnectorCommand.Sync())
            advanceUntilIdle()

            val terminal = proc.await(id)
            terminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
            terminal.error.shouldBeInstanceOf<ConnectorTimeoutException>()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `timed out op clears busy state and the actor keeps processing`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val (proc, job) = buildProcessor(
                timeouts = shortTimeouts,
                executor = { cmd ->
                    if (cmd is ConnectorCommand.Sync) awaitCancellation() else executed += cmd
                },
            )

            proc.submit(ConnectorCommand.Sync())
            val nextId = proc.submit(ConnectorCommand.Reset)
            advanceUntilIdle()

            // isBusy is derived from Queued/Processing entries — a wedged op must not keep it true.
            proc.operations.first()
                .none { it is ConnectorOperation.Queued || it is ConnectorOperation.Processing } shouldBe true
            proc.await(nextId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()
            executed.shouldHaveSize(1)

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `NonCancellable executor still reaches a terminal state`() = runTest2 {
            // A plain cancellable fake would pass even if the bound were inert. This one models the
            // real hazard: work that ignores cancellation entirely.
            val released = CompletableDeferred<Unit>()
            val (proc, job) = buildProcessor(
                timeouts = shortTimeouts,
                executor = {
                    withContext(NonCancellable) {
                        withTimeoutOrNull(30.seconds) { awaitCancellation() }
                        released.complete(Unit)
                    }
                },
            )

            val id = proc.submit(ConnectorCommand.Sync())
            advanceUntilIdle()

            released.isCompleted shouldBe true
            val terminal = proc.await(id)
            terminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
            terminal.error.shouldBeInstanceOf<ConnectorTimeoutException>()

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `thread-blocking executor is bounded once it returns`() = runTest2 {
            // Real dispatchers on purpose: the hazard here is a body that blocks a thread and so
            // cannot be interrupted by cancellation at all. Virtual time cannot model that, and a
            // fake that merely suspends would pass even if the bound were inert.
            withContext(Dispatchers.Default) {
                val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                val processor = ConnectorProcessor(
                    connectorId = connectorId,
                    syncSettings = syncSettings,
                    timeouts = ConnectorProcessor.Timeouts(
                        sync = 200.milliseconds,
                        destructive = 10.seconds,
                        local = 10.seconds,
                    ),
                    executor = { cmd -> if (cmd is ConnectorCommand.Sync) Thread.sleep(1000) },
                )
                processor.start(scope)

                // The blocking call outlives its bound by far, yet the op still resolves — and as
                // Failed, never Succeeded.
                val terminal = processor.await(processor.submit(ConnectorCommand.Sync()))
                terminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
                terminal.error.shouldBeInstanceOf<ConnectorTimeoutException>()

                // ...and the actor survived it.
                processor.await(processor.submit(ConnectorCommand.Reset))
                    .shouldBeInstanceOf<ConnectorOperation.Succeeded>()

                scope.cancel()
            }
        }

        @Test
        fun `destructive commands get the destructive bound`() = runTest2 {
            val (proc, job) = buildProcessor(
                timeouts = shortTimeouts,
                executor = { awaitCancellation() },
            )

            val syncId = proc.submit(ConnectorCommand.Sync())
            val resetId = proc.submit(ConnectorCommand.Reset)
            advanceUntilIdle()

            val syncTerminal = proc.await(syncId)
            val resetTerminal = proc.await(resetId)
            syncTerminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
            resetTerminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
            (syncTerminal.error as ConnectorTimeoutException).timeout shouldBe shortTimeouts.sync
            (resetTerminal.error as ConnectorTimeoutException).timeout shouldBe shortTimeouts.destructive

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `cancellation` {

        @Test
        fun `cancel terminates an in-flight op without stopping the actor`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val (proc, job) = buildProcessor(executor = { cmd ->
                if (cmd is ConnectorCommand.Sync) awaitCancellation() else executed += cmd
            })

            val syncId = proc.submit(ConnectorCommand.Sync())
            advanceUntilIdle()

            proc.cancel(syncId) shouldBe true
            advanceUntilIdle()

            val terminal = proc.await(syncId)
            terminal.shouldBeInstanceOf<ConnectorOperation.Failed>()
            // Not a CancellationException: the waiter must see a connector failure, not its own
            // coroutine being cancelled.
            terminal.error.shouldBeInstanceOf<ConnectorCancelledException>()

            val nextId = proc.submit(ConnectorCommand.Reset)
            advanceUntilIdle()
            proc.await(nextId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()
            executed.shouldHaveSize(1)

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `cancelling a queued op releases its waiter immediately and never runs it`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val gate = CompletableDeferred<Unit>()
            val (proc, job) = buildProcessor(executor = { cmd ->
                executed += cmd
                if (executed.size == 1) gate.await()
            })

            proc.submit(ConnectorCommand.Sync()) // becomes Processing on the gate
            advanceUntilIdle()
            val queuedId = proc.submit(ConnectorCommand.Sync()) // queued behind it

            proc.cancel(queuedId) shouldBe true
            // No advance: the waiter resolves without the actor ever reaching the op.
            proc.await(queuedId).shouldBeInstanceOf<ConnectorOperation.Failed>()

            gate.complete(Unit)
            advanceUntilIdle()
            executed.shouldHaveSize(1)

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `destructive commands are not cancelled mid-flight`() = runTest2 {
            // A destructive command reported failed locally may already have taken effect remotely,
            // so it is never aborted on request — it runs to completion.
            val gate = CompletableDeferred<Unit>()
            val completed = mutableListOf<ConnectorCommand>()
            val (proc, job) = buildProcessor(executor = { cmd ->
                gate.await()
                completed += cmd
            })

            val resetId = proc.submit(ConnectorCommand.Reset)
            advanceUntilIdle()

            proc.cancel(resetId) shouldBe false
            advanceUntilIdle()

            gate.complete(Unit)
            advanceUntilIdle()

            proc.await(resetId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()
            completed.shouldHaveSize(1)

            job.cancel()
            advanceUntilIdle()
        }
    }

    @Nested
    inner class `coalescing` {

        @Test
        fun `queued syncs fold into one op instead of growing the inbox`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val gate = CompletableDeferred<Unit>()
            val (proc, job) = buildProcessor(executor = { cmd ->
                executed += cmd
                if (executed.size == 1) gate.await()
            })

            proc.submit(ConnectorCommand.Sync()) // becomes Processing on the gate
            advanceUntilIdle()

            val ids = (1..50).map { proc.submit(ConnectorCommand.Sync()) }
            ids.toSet().shouldHaveSize(1)

            gate.complete(Unit)
            advanceUntilIdle()

            // The 50 requests were served by a single additional execution.
            executed.shouldHaveSize(2)
            ids.forEach { proc.await(it).shouldBeInstanceOf<ConnectorOperation.Succeeded>() }

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `flood at a cadence shorter than the command timeout does not grow the inbox`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val (proc, job) = buildProcessor(
                timeouts = ConnectorProcessor.Timeouts(
                    sync = 60.seconds,
                    destructive = 60.seconds,
                    local = 60.seconds,
                ),
                executor = { cmd ->
                    executed += cmd
                    delay(50.seconds) // slower than the request cadence, faster than the bound
                },
            )

            repeat(100) {
                proc.submit(ConnectorCommand.Sync())
                advanceTimeBy(1.seconds)
            }
            advanceUntilIdle()

            // Requests arriving while one command runs collapse into a single queued follow-up, so
            // the queue can never outgrow "one running + one queued".
            proc.operations.first().count { it is ConnectorOperation.Queued } shouldBe 0
            executed.size shouldBeLessThan 10

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `coalesced options are the union of every folded request`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val gate = CompletableDeferred<Unit>()
            val (proc, job) = buildProcessor(executor = { cmd ->
                executed += cmd
                if (executed.size == 1) gate.await()
            })

            proc.submit(ConnectorCommand.Sync())
            advanceUntilIdle()

            proc.submit(ConnectorCommand.Sync(SyncOptions(stats = false, readData = false, writeData = true)))
            proc.submit(ConnectorCommand.Sync(SyncOptions(stats = false, readData = true, writeData = false)))

            gate.complete(Unit)
            advanceUntilIdle()

            val merged = (executed.last() as ConnectorCommand.Sync).options
            merged.readData shouldBe true
            merged.writeData shouldBe true

            job.cancel()
            advanceUntilIdle()
        }

        @Test
        fun `a running sync is never folded into`() = runTest2 {
            val executed = mutableListOf<ConnectorCommand>()
            val gate = CompletableDeferred<Unit>()
            val (proc, job) = buildProcessor(executor = { cmd ->
                executed += cmd
                if (executed.size == 1) gate.await()
            })

            val runningId = proc.submit(ConnectorCommand.Sync())
            advanceUntilIdle()

            val laterId = proc.submit(ConnectorCommand.Sync())
            (laterId == runningId) shouldBe false

            gate.complete(Unit)
            advanceUntilIdle()
            executed.shouldHaveSize(2)

            job.cancel()
            advanceUntilIdle()
        }
    }
}
