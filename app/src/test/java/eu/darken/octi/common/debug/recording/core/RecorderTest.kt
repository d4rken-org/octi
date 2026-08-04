package eu.darken.octi.common.debug.recording.core

import android.app.Application
import eu.darken.octi.common.debug.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import java.io.File

/**
 * The REAL recorder with a fault injected into the logging its own teardown emits - no double, no
 * manual cleanup. [Recorder.stop] has to run every teardown step regardless of what the ones before
 * it did: the module commits "stopped" state around it, and a logger left installed keeps writing
 * into a session everybody else considers finished.
 *
 * Robolectric because the file logger needs android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class RecorderTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Fails on every line it receives while armed - the loggers being torn down are receivers. */
    private class Saboteur : Logging.Logger {
        @Volatile
        var armed = false

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            if (armed) throw IllegalStateException("Simulated logger failure")
        }
    }

    @Test
    fun `a stop whose logging fails still tears the recorder down`() {
        val loggersBefore = Logging.loggers
        val sessionDir = tempFolder.newFolder("session")
        val recorder = Recorder()
        val saboteur = Saboteur()

        try {
            runBlocking { recorder.start(sessionDir) }
            Logging.install(saboteur)
            saboteur.armed = true

            // The first failure is reported, not swallowed - but only after everything ran.
            shouldThrow<IllegalStateException> { runBlocking { recorder.stop() } }
        } finally {
            saboteur.armed = false
            Logging.remove(saboteur)
        }

        // Nothing was uninstalled by hand in between: this is stop()'s own guarantee.
        Logging.loggers shouldBe loggersBefore
        recorder.isRecording shouldBe false
        recorder.path.shouldBeNull()
        recorder.sessionDir.shouldBeNull()
        // The end marker is written by the file logger's own stop, so the writer was closed too
        // instead of being left open on a session that is reported as finished.
        File(sessionDir, "core.log").readText() shouldContain "=== END ==="
    }
}
