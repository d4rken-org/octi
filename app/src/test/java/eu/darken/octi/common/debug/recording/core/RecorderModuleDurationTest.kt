package eu.darken.octi.common.debug.recording.core

import android.app.Application
import android.content.Context
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Clock

/**
 * The "that recording looks very short" prompt is a duration heuristic, and duration was measured
 * against the wall clock. A clock adjustment mid-recording (NTP sync, the user changing the time)
 * therefore either invented a long recording out of a short one or trapped a long recording in the
 * warning. A live session now measures monotonically; only a session resumed after process death
 * has to fall back to the wall-clock start the trigger file persisted, because monotonic time does
 * not survive a reboot.
 *
 * Robolectric with the REAL [Recorder]: the module constructs it directly, and its FileLogger
 * needs android.util.Log.
 *
 * The start/stop failure cases live here too: they need exactly this harness — a real recorder
 * whose globally installed loggers are accounted for, inside a hard timeout envelope.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecorderModuleDurationTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Test-controlled clocks, handed to the module's two seams. The durations under test are
    // wall-clock/monotonic, so virtual time cannot drive them. Volatile: the seams are read on the
    // module's own coroutines, the fields are written from the test thread.
    private class TestClocks(
        @Volatile var wall: Long,
        @Volatile var monotonic: Long,
    )

    /**
     * The recorder is stopped in a nested finally, before the scope goes: cancelling the scope alone
     * does NOT uninstall a running recorder's globally installed loggers, and the forward-jump case
     * deliberately ends still recording. The accounting covers loggers of ANY type — this recorder
     * installs a LogCatLogger alongside the FileLogger.
     *
     * [seed] runs BEFORE the module is constructed: a trigger file makes the module resume during
     * construction, so seeding afterwards would race the init collector. The clock seams are
     * installed immediately after construction, so a resume's trigger validation may still see the
     * real clock — the resume cases anchor their timestamps near real time for exactly that reason.
     *
     * [recorderOverride] is installed after construction as well, so it only reaches attempts the
     * test itself requests, not one a seeded trigger starts during construction.
     */
    private fun withModule(
        clocks: TestClocks,
        awaitRecording: Boolean = false,
        recorderOverride: Recorder? = null,
        seed: (externalDir: File) -> Unit = {},
        block: suspend (RecorderModule) -> Unit,
    ) {
        val loggersBefore = Logging.loggers
        val externalDir = tempFolder.newFolder("external")
        val cacheRoot = tempFolder.newFolder("cache")
        File(externalDir, "debug/logs").mkdirs()
        File(cacheRoot, "debug/logs").mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.getExternalFilesDir(null) } returns externalDir
        every { context.cacheDir } returns cacheRoot
        seed(externalDir)

        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var module: RecorderModule? = null
        try {
            try {
                val created = RecorderModule(
                    context = context,
                    appScope = appScope,
                    dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
                ).apply {
                    wallClock = { clocks.wall }
                    monotonicClock = { clocks.monotonic }
                    recorderOverride?.let { override -> recorderFactory = { override } }
                }
                module = created
                // Envelope: a wedged start or stop must fail in seconds, not hold the gradle worker.
                runBlocking {
                    withTimeout(TEST_ENVELOPE_MS) {
                        if (awaitRecording) created.state.first { it.isRecording }
                        block(created)
                    }
                }
            } finally {
                module?.let { runBlocking { withTimeoutOrNull(TEST_ENVELOPE_MS) { it.stopRecorder() } } }
            }
        } finally {
            appScope.cancel()
            // Remove stragglers after asserting so one failure can't cascade into later tests.
            val leaked = Logging.loggers - loggersBefore.toSet()
            leaked.forEach { Logging.remove(it) }
            leaked shouldBe emptyList<Logging.Logger>()
        }
    }

    private fun seedSession(externalDir: File, name: String): File {
        val sessionDir = File(externalDir, "debug/logs/$name").also { it.mkdirs() }
        check(File(sessionDir, "core.log").createNewFile()) { "Failed to seed core.log" }
        return sessionDir
    }

    @Test
    fun `an eight second recording warns`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            clocks.monotonic += 8_000L
            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true

            // "Stop anyway" is the user's own next step, and past the threshold it stops cleanly.
            clocks.monotonic += 3_000L
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a ten second recording stops`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            clocks.monotonic += 10_000L

            val result = module.requestStopRecorder()
            result.shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            result.session.sessionDir.isDirectory shouldBe true
            result.session.coreLogFile.exists() shouldBe true
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a backward wall-clock jump does not warn on a long recording`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            // Twelve real seconds of recording, and an NTP sync that moves the wall clock an hour
            // back. Wall-clock measurement would report a negative duration here.
            clocks.monotonic += 12_000L
            clocks.wall -= 3_600_000L

            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a forward wall-clock jump does not skip the warning`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        withModule(clocks) { module ->
            module.startRecorder()

            // Three real seconds of recording, and a clock correction an hour forward. Wall-clock
            // measurement would call this a one-hour recording and skip the prompt.
            clocks.monotonic += 3_000L
            clocks.wall += 3_600_000L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true
        }
    }

    @Test
    fun `a resumed session measures from the persisted start time`() {
        // Resumed after a process death: there is no monotonic base to measure against, so the wall
        // time the trigger file persisted is all the module has. Anchored near real time because the
        // trigger parser rejects future timestamps and may still run on the real clock.
        val realNow = Clock.System.now().toEpochMilliseconds()
        val startedAt = realNow - 8_000L
        val clocks = TestClocks(wall = realNow, monotonic = 100_000L)

        withModule(
            clocks,
            awaitRecording = true,
            seed = { externalDir ->
                val sessionDir = seedSession(externalDir, "${BuildConfigWrap.APPLICATION_ID}_1.0_resume")
                File(externalDir, "force_debug_run").writeText("${sessionDir.absolutePath}\n$startedAt")
            },
        ) { module ->
            // Proves the trigger path was taken, not an accidental directory scan.
            module.state.first().recordingStartedAt shouldBe startedAt

            clocks.wall = startedAt + 8_000L
            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort

            clocks.wall = startedAt + 10_000L
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a clock rollback across a resume fails open`() {
        // The wall clock moved backward after the recording started, so the persisted start time now
        // lies in the future. A negative duration must not trap the user in the warning. (A trigger
        // seeded with a future timestamp is undrivable: the parser rejects those outright.)
        val realNow = Clock.System.now().toEpochMilliseconds()
        val startedAt = realNow - 8_000L
        val clocks = TestClocks(wall = realNow, monotonic = 100_000L)

        withModule(
            clocks,
            awaitRecording = true,
            seed = { externalDir ->
                val sessionDir = seedSession(externalDir, "${BuildConfigWrap.APPLICATION_ID}_1.0_rollback")
                File(externalDir, "force_debug_run").writeText("${sessionDir.absolutePath}\n$startedAt")
            },
        ) { module ->
            module.state.first().recordingStartedAt shouldBe startedAt

            clocks.wall = startedAt - 3_600_000L
            module.requestStopRecorder().shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
        }
    }

    @Test
    fun `a repeat recording reusing an old session stays monotonic`() {
        // An unzipped session directory left behind by a failed compression is picked up again by
        // the next ordinary recording. Keying the "no monotonic base" case on directory reuse
        // instead of on the trigger file would route that live recording to the stale directory's
        // mtime — a wall-clock measurement, and the wrong one at that.
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        var reusableDir: File? = null

        withModule(
            clocks,
            seed = { externalDir ->
                reusableDir = seedSession(externalDir, "${BuildConfigWrap.APPLICATION_ID}_1.0_leftover")
            },
        ) { module ->
            // Non-vacuity: the leftover directory really is the one being recorded into again, so
            // the assertion below is about which start time wins, not about a fresh directory.
            module.startRecorder().sessionDir shouldBe reusableDir

            clocks.monotonic += 3_000L
            clocks.wall += 3_600_000L

            module.requestStopRecorder() shouldBe RecorderModule.StopResult.TooShort
            module.state.first().isRecording shouldBe true
        }
    }

    /**
     * A DIRECTORY at the trigger path: [RecorderModule.readTriggerFile] tolerates it (it reads as
     * "no resumable session"), but the trigger write at the end of the start sequence cannot open
     * it. That is the last step of the start branch, so the recorder is already live and globally
     * installed when it throws — the orphan case.
     *
     * Note this also makes `triggerFile.exists()` true, so the module treats it as a process
     * resume and starts recording during construction; [withModule] is therefore driven with
     * awaitRecording=false.
     */
    private fun seedBlockedTrigger(externalDir: File): File =
        File(externalDir, "force_debug_run").also { check(it.mkdirs()) { "Failed to seed trigger dir" } }

    @Test
    fun `a failed start surfaces the error instead of hanging`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)

        withModule(clocks, seed = { seedBlockedTrigger(it) }) { module ->
            // Asserted INSIDE the envelope and on the concrete exception type: a wedged await
            // fails with TimeoutCancellationException, which must not be able to satisfy this.
            shouldThrow<FileNotFoundException> { module.startRecorder() }

            val state = module.state.first()
            state.isRecording shouldBe false
            state.shouldRecord shouldBe false
            state.startFailure.shouldBeInstanceOf<FileNotFoundException>()
            // No leaked logger: the harness asserts it, and it is the proof that the recorder the
            // failed attempt had already started was rolled back instead of orphaned.
        }
    }

    @Test
    fun `the recorder recovers after a failed start`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        var blockedTrigger: File? = null

        withModule(clocks, seed = { blockedTrigger = seedBlockedTrigger(it) }) { module ->
            shouldThrow<FileNotFoundException> { module.startRecorder() }

            // The blocker was NOT created by the failed attempt, so its rollback left it alone.
            blockedTrigger!!.delete() shouldBe true

            // Collector still alive and re-armed: shouldRecord was reset by the failure, so this
            // request is a fresh edge.
            val session = module.startRecorder()
            session.sessionDir.isDirectory shouldBe true

            val started = module.state.first()
            started.isRecording shouldBe true
            started.startFailure.shouldBeNull()

            module.stopRecorder().shouldNotBeNull()
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a boot trigger that cannot start settles instead of wedging`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)

        withModule(clocks, seed = { seedBlockedTrigger(it) }) { module ->
            // Nobody is waiting on this attempt — it is the one the module makes on its own during
            // construction. It has to settle rather than crash the app scope or spin.
            val settled = module.state.first { it.startFailure != null }
            settled.isRecording shouldBe false
            settled.shouldRecord shouldBe false
            settled.startFailure.shouldBeInstanceOf<FileNotFoundException>()
        }
    }

    @Test
    fun `a start that cannot open the log file surfaces the error`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)

        withModule(
            clocks,
            seed = { externalDir ->
                // An existing session whose core.log is a DIRECTORY: the file logger cannot open a
                // writer for it. This fails inside the start operation itself, before the trigger
                // write, and it used to be swallowed into a successful start with a logger that
                // wrote nowhere.
                val sessionDir = File(externalDir, "debug/logs/${BuildConfigWrap.APPLICATION_ID}_1.0_blocked")
                    .also { it.mkdirs() }
                check(File(sessionDir, "core.log").mkdirs()) { "Failed to seed core.log dir" }
            },
        ) { module ->
            shouldThrow<FileNotFoundException> { module.startRecorder() }

            val state = module.state.first()
            state.isRecording shouldBe false
            state.shouldRecord shouldBe false
            state.startFailure.shouldBeInstanceOf<FileNotFoundException>()
        }
    }

    @Test
    fun `a stop that fails still completes`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)
        val loggersBefore = Logging.loggers.toSet()
        val recorder = spyk(Recorder())

        withModule(clocks, recorderOverride = recorder) { module ->
            module.startRecorder()
            module.state.first().isRecording shouldBe true

            coEvery { recorder.stop() } throws IOException("Simulated stop failure")

            try {
                // Bounded by the envelope: a throwing stop must not strand the caller, and the
                // module commits the cleared state anyway — the session is reported as stopped
                // exactly once, which is what the compression downstream keys off.
                module.stopRecorder().shouldNotBeNull()

                val state = module.state.first()
                state.isRecording shouldBe false
                state.shouldRecord shouldBe false
            } finally {
                // The double throws INSTEAD of running the real stop, so the loggers the real start
                // installed are uninstalled here. Recorder.stop() itself guarantees that removal
                // even when a step fails, but that guarantee cannot be driven through this double.
                (Logging.loggers - loggersBefore).forEach { Logging.remove(it) }
            }
        }
    }

    @Test
    fun `concurrent stop requests stop the session exactly once`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)

        withModule(clocks) { module ->
            module.startRecorder()
            clocks.monotonic += 30_000L

            val results = coroutineScope {
                (1..4).map { async(Dispatchers.IO) { module.requestStopRecorder() } }.awaitAll()
            }

            // Deciding outside the request lock let every caller read "recording" and then report a
            // stop that only one of them performed.
            results.count { it is RecorderModule.StopResult.Stopped } shouldBe 1
            results.count { it == RecorderModule.StopResult.NotRecording } shouldBe 3
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a force stop racing a stop request stops the session exactly once`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)

        withModule(clocks) { module ->
            module.startRecorder()
            clocks.monotonic += 30_000L

            val (forced, requested) = coroutineScope {
                val force = async(Dispatchers.IO) { module.stopRecorder() }
                val request = async(Dispatchers.IO) { module.requestStopRecorder() }
                force.await() to request.await()
            }

            listOf(
                forced != null,
                requested is RecorderModule.StopResult.Stopped,
            ).count { it } shouldBe 1
            module.state.first().isRecording shouldBe false
        }
    }

    @Test
    fun `a stop request racing a start never claims the live session`() {
        val clocks = TestClocks(wall = WALL_BASE, monotonic = 100_000L)

        withModule(clocks) { module ->
            module.startRecorder()
            clocks.monotonic += 30_000L

            val (requested, restarted) = coroutineScope {
                val request = async(Dispatchers.IO) { module.requestStopRecorder() }
                val start = async(Dispatchers.IO) { module.startRecorder() }
                request.await() to start.await()
            }

            requested.shouldBeInstanceOf<RecorderModule.StopResult.Stopped>()
            // Either order is legal, but the reported stop and a still-live session can never be
            // the same one - that pairing is what an unsynchronized read produced.
            if (module.state.first().isRecording) {
                requested.session.sessionDir shouldNotBe restarted.sessionDir
            } else {
                requested.session.sessionDir shouldBe restarted.sessionDir
            }
        }
    }

    companion object {
        // Independent of any production bound: a wedged wait has to fail the test, not hang the
        // gradle worker.
        private const val TEST_ENVELOPE_MS = 10_000L
        private const val WALL_BASE = 1_800_000_000_000L
    }
}
