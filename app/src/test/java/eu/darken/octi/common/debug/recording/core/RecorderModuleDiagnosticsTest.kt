package eu.darken.octi.common.debug.recording.core

import android.app.Application
import android.content.Context
import eu.darken.octi.common.debug.logging.Logging
import eu.darken.octi.common.upgrade.UpgradeDiagnostics
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis

/**
 * The flavor billing diagnostics belong in the recording's own header. The read that produces them
 * lives outside the recorder and happens while the module is committing a start, so it is bounded
 * and guarded: a source that never answers, or one that fails, must not cost the user the recording
 * they went looking for while the app was already misbehaving.
 *
 * Robolectric with the REAL [Recorder] and real dispatchers: the header line has to arrive in the
 * session's log file, whose FileLogger needs android.util.Log, and the read deadline is wall-clock,
 * so virtual time would skip past it instead of exercising it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecorderModuleDiagnosticsTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val logLines = CopyOnWriteArrayList<String>()
    private val logCapture = object : Logging.Logger {
        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            logLines.add(message)
        }
    }

    @Before
    fun installLogCapture() {
        Logging.install(logCapture)
    }

    @After
    fun removeLogCapture() {
        Logging.remove(logCapture)
    }

    /**
     * The recorder is stopped in a nested finally, before the scope goes: cancelling the scope alone
     * does NOT uninstall a running recorder's globally installed loggers. The accounting covers
     * loggers of ANY type — this recorder installs a LogCatLogger alongside the FileLogger — and it
     * is what proves a start that aborted in the header left nothing behind.
     */
    private fun withModule(
        upgradeDiagnostics: UpgradeDiagnostics,
        headerTimeoutMs: Long = 300L,
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

        val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var module: RecorderModule? = null
        try {
            try {
                val created = RecorderModule(
                    context = context,
                    appScope = appScope,
                    dispatcherProvider = TestDispatcherProvider(Dispatchers.IO),
                    upgradeDiagnostics = upgradeDiagnostics,
                ).apply { headerReadTimeoutMs = headerTimeoutMs }
                module = created
                // Envelope: a wedged header read must fail in seconds, not hold the gradle worker.
                runBlocking {
                    withTimeout(TEST_ENVELOPE_MS) { block(created) }
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

    private fun diagnostics(stub: suspend () -> String?): UpgradeDiagnostics =
        mockk<UpgradeDiagnostics>().apply { coEvery { debugInfo() } coAnswers { stub() } }

    private fun String.countOf(needle: String): Int = Regex(Regex.escape(needle)).findAll(this).count()

    @Test
    fun `a diagnostics string reaches the recording header`() {
        withModule(diagnostics { GPLAY_DIAGNOSTICS }) { module ->
            val session = module.startRecorder()

            val expected = "Upgrade diagnostics: $GPLAY_DIAGNOSTICS"
            logLines.count { it == expected } shouldBe 1

            module.stopRecorder()

            // The bus assertion above passes even for a header written before the recorder is live;
            // only the recording itself proves the operator gets the diagnostics with the log.
            session.coreLogFile.readText().countOf(expected) shouldBe 1
        }
    }

    @Test
    fun `a flavor with nothing to report gets no diagnostics line`() {
        withModule(diagnostics { null }) { module ->
            module.startRecorder()

            module.state.first().isRecording shouldBe true
            // Above all not an "unavailable" one: FOSS has nothing to say, it did not fail to say it.
            logLines.any { it.startsWith("Upgrade diagnostics") } shouldBe false
        }
    }

    @Test
    fun `a failing diagnostics read still leaves a tracked recording`() {
        withModule(diagnostics { throw IllegalStateException("cache unreadable") }) { module ->
            val session = module.startRecorder()

            session.sessionDir.isDirectory shouldBe true
            module.state.first().isRecording shouldBe true
            logLines.any { it.startsWith("Upgrade diagnostics unavailable:") } shouldBe true
        }
    }

    @Test
    fun `a wedged diagnostics read does not hold up the recording`() {
        withModule(diagnostics { awaitCancellation() }, headerTimeoutMs = 300L) { module ->
            val elapsed = measureTimeMillis { module.startRecorder() }

            module.state.first().isRecording shouldBe true
            logLines.any { it.startsWith("Upgrade diagnostics unavailable, read did not finish") } shouldBe true
            // Non-vacuity: without the bound this would sit on the wedged read forever.
            elapsed shouldBeLessThan 1_500L
        }
    }

    @Test
    fun `a cancelled diagnostics read does not leak the started recorder`() {
        // Cancellation is the one thing the guarded read rethrows, and the recorder is already live
        // when it does. The module's own scope is alive, so the caller gets an ordinary failure.
        withModule(diagnostics { throw CancellationException("scope died mid-read") }) { module ->
            shouldThrow<RecorderModule.RecorderStartFailedException> { module.startRecorder() }

            module.state.first().isRecording shouldBe false
            // No leaked logger: the harness asserts it, and it is the proof that the recorder the
            // aborted header left behind was stopped instead of orphaned.
        }
    }

    companion object {
        // Independent of any production bound: a wedged wait has to fail the test, not hang the
        // gradle worker.
        private const val TEST_ENVELOPE_MS = 10_000L

        private const val GPLAY_DIAGNOSTICS =
            "BillingCache(lastProStateAt=never, lastProStateSku=unknown/legacy, proUnconfirmedSince=none), " +
                "ProHistory=ProHistory(lastState=FREE, graceEngagedCount=0, graceEngagedLast=null, " +
                "proLostCount=0, proLostLast=null)"
    }
}
