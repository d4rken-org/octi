package eu.darken.octi.sync.core

import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.errors.ConnectorPausedException
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
    private lateinit var pausedConnectorsValue: MutableStateFlow<Set<ConnectorId>>

    @BeforeEach
    fun setup() {
        pausedConnectorsValue = MutableStateFlow(emptySet())
        syncSettings = mockk(relaxed = true) {
            every { pausedConnectors } returns mockk<DataStoreValue<Set<ConnectorId>>>(relaxed = true) {
                every { flow } returns pausedConnectorsValue
                coEvery { update(any()) } coAnswers {
                    val transform = firstArg<(Set<ConnectorId>) -> Set<ConnectorId>?>()
                    val old = pausedConnectorsValue.value
                    val new = transform(old) ?: old
                    pausedConnectorsValue.value = new
                    mockk(relaxed = true)
                }
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
                ConnectorCommand.Pause -> pausedConnectorsValue.value = pausedConnectorsValue.value + connectorId
                ConnectorCommand.Resume -> pausedConnectorsValue.value = pausedConnectorsValue.value - connectorId
                else -> executed += cmd
            }
        })

        proc.submit(ConnectorCommand.Pause)
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
                ConnectorCommand.Pause -> pausedConnectorsValue.value = pausedConnectorsValue.value + connectorId
                ConnectorCommand.Resume -> pausedConnectorsValue.value = pausedConnectorsValue.value - connectorId
                else -> Unit
            }
        })
        pausedConnectorsValue.value = setOf(connectorId)

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
                ConnectorCommand.Pause -> pausedConnectorsValue.value = pausedConnectorsValue.value + connectorId
                ConnectorCommand.Resume -> pausedConnectorsValue.value = pausedConnectorsValue.value - connectorId
                else -> Unit
            }
        })
        pausedConnectorsValue.value = setOf(connectorId)

        // User first awaits Resume (as happens via togglePause → execute(Resume)) — by the time
        // Resume's terminal resolves, the side-effect Sync has already been submitted into the
        // inbox. Any subsequent user submit (Pause) is strictly ordered after that Sync.
        val resumeId = proc.submit(ConnectorCommand.Resume)
        advanceUntilIdle()
        proc.await(resumeId).shouldBeInstanceOf<ConnectorOperation.Succeeded>()

        proc.submit(ConnectorCommand.Pause)
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
}
