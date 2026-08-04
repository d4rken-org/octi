package eu.darken.octi.common.debug.logging

import android.app.Application
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import java.io.File
import java.io.IOException

/**
 * A start that cannot open its writer removes the log file again - which used to include the log of
 * a session being RESUMED, so a restart that failed threw away the recording the user had already
 * collected. Only a file this attempt created may be removed.
 *
 * Robolectric because the logger uses android.util.Log (app-common has no test runner of its own).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class FileLoggerTest : BaseTest() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a failed start keeps a log it did not create`() {
        val logFile = File(tempFolder.newFolder("session"), "core.log")
        logFile.writeText("=== BEGIN ===\nthe recording the user already made\n")
        logFile.setWritable(false, false)
        // A root test runner ignores the permission bit, and then there is no failure to observe.
        assumeTrue("Log file is still writable", !logFile.canWrite())

        try {
            shouldThrow<IOException> { FileLogger(logFile).start() }

            logFile.exists() shouldBe true
            logFile.readText() shouldContain "the recording the user already made"
        } finally {
            logFile.setWritable(true, true)
        }
    }
}
