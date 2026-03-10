package eu.darken.octi.common.debug.recording.core

import android.content.Context
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.DispatcherProvider
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File

class RecorderModuleTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private lateinit var triggerFile: File
    private lateinit var logDir: File
    private lateinit var cacheLogDir: File
    private lateinit var module: RecorderModule

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setup() {
        triggerFile = File(File(tempDir, "external"), "force_debug_run")
        logDir = File(tempDir, "external/debug/logs").also { it.mkdirs() }
        cacheLogDir = File(tempDir, "cache/debug/logs").also { it.mkdirs() }

        val context = mockk<Context>(relaxed = true) {
            every { getExternalFilesDir(null) } returns File(tempDir, "external")
            every { cacheDir } returns File(tempDir, "cache")
        }
        val dispatcherProvider = mockk<DispatcherProvider> {
            every { IO } returns testDispatcher
            every { Default } returns testDispatcher
            every { Main } returns testDispatcher
            every { Unconfined } returns testDispatcher
        }

        module = RecorderModule(
            context = context,
            appScope = TestScope(testDispatcher),
            dispatcherProvider = dispatcherProvider,
        )
    }

    private fun createSessionDir(name: String, withCoreLog: Boolean = true): File {
        val dir = File(logDir, name).also { it.mkdirs() }
        if (withCoreLog) {
            File(dir, "core.log").writeText("=== BEGIN ===\nsome log content\n")
        }
        return dir
    }

    @Nested
    inner class WriteTriggerFile {
        @Test
        fun `stores sessionDir and startedAt`() {
            val sessionDir = File(tempDir, "test-session")
            module.writeTriggerFile(sessionDir, 1709312400000L)

            triggerFile.shouldExist()
            val lines = triggerFile.readLines()
            lines[0] shouldBe sessionDir.absolutePath
            lines[1] shouldBe "1709312400000"
        }

        @Test
        fun `overwrites existing trigger file`() {
            triggerFile.writeText("old content")
            val sessionDir = File(tempDir, "new-session")
            module.writeTriggerFile(sessionDir, 99L)

            val lines = triggerFile.readLines()
            lines[0] shouldBe sessionDir.absolutePath
            lines[1] shouldBe "99"
        }
    }

    @Nested
    inner class ReadTriggerFile {
        @Test
        fun `parses valid content`() {
            val sessionDir = File(tempDir, "my-session")
            val startedAt = System.currentTimeMillis() - 60_000
            triggerFile.writeText("${sessionDir.absolutePath}\n$startedAt")

            val result = module.readTriggerFile()
            result.shouldNotBeNull()
            result.first shouldBe sessionDir
            result.second shouldBe startedAt
        }

        @Test
        fun `returns null for missing file`() {
            module.readTriggerFile().shouldBeNull()
        }

        @Test
        fun `returns null for empty file`() {
            triggerFile.writeText("")
            module.readTriggerFile().shouldBeNull()
        }

        @Test
        fun `returns null for single line file`() {
            triggerFile.writeText("/some/path")
            module.readTriggerFile().shouldBeNull()
        }

        @Test
        fun `returns null for invalid timestamp`() {
            triggerFile.writeText("/some/path\nnot_a_number")
            module.readTriggerFile().shouldBeNull()
        }

        @Test
        fun `returns null for future timestamp`() {
            val futureTime = System.currentTimeMillis() + 86_400_000
            triggerFile.writeText("/some/path\n$futureTime")
            module.readTriggerFile().shouldBeNull()
        }

        @Test
        fun `returns null for zero timestamp`() {
            triggerFile.writeText("/some/path\n0")
            module.readTriggerFile().shouldBeNull()
        }

        @Test
        fun `returns null for negative timestamp`() {
            triggerFile.writeText("/some/path\n-1")
            module.readTriggerFile().shouldBeNull()
        }
    }

    @Nested
    inner class FindOrCreateSession {
        @Test
        fun `resumes existing session from trigger file`() {
            val sessionDir = createSessionDir("eu.darken.octi_1.0_1709312400000")
            val startedAt = System.currentTimeMillis() - 120_000
            module.writeTriggerFile(sessionDir, startedAt)

            val (resultDir, resultStartedAt) = module.findOrCreateSession()
            resultDir shouldBe sessionDir
            resultStartedAt shouldBe startedAt
        }

        @Test
        fun `preserves original startedAt on resume`() {
            val sessionDir = createSessionDir("eu.darken.octi_1.0_1709312400000")
            val originalStartedAt = System.currentTimeMillis() - 3_600_000
            module.writeTriggerFile(sessionDir, originalStartedAt)

            val (_, resultStartedAt) = module.findOrCreateSession()
            resultStartedAt shouldBe originalStartedAt
        }

        @Test
        fun `creates new session when trigger references missing dir`() {
            val missingDir = File(logDir, "eu.darken.octi_1.0_gone")
            module.writeTriggerFile(missingDir, System.currentTimeMillis() - 1000)

            val (resultDir, _) = module.findOrCreateSession()
            resultDir.isDirectory shouldBe true
            resultDir shouldBe File(logDir, resultDir.name)
        }

        @Test
        fun `creates new session when trigger references dir without core log`() {
            val emptySession = File(logDir, "eu.darken.octi_1.0_empty").also { it.mkdirs() }
            module.writeTriggerFile(emptySession, System.currentTimeMillis() - 1000)

            val (resultDir, _) = module.findOrCreateSession()
            resultDir.absolutePath shouldBe File(logDir, resultDir.name).absolutePath
        }

        @Test
        fun `legacy empty trigger falls back to dir scan`() {
            triggerFile.writeText("")
            val sessionDir = createSessionDir("eu.darken.octi_1.0_1709312400000")

            val (resultDir, _) = module.findOrCreateSession()
            resultDir shouldBe sessionDir
        }

        @Test
        fun `creates fresh session when no trigger and no existing dirs`() {
            val (resultDir, resultStartedAt) = module.findOrCreateSession()
            resultDir.isDirectory shouldBe true
            resultDir.name shouldContain BuildConfigWrap.APPLICATION_ID
            resultStartedAt shouldBeGreaterThan 0L
        }
    }

    @Nested
    inner class FindExistingSessionDir {
        @Test
        fun `finds session dir with core log`() {
            val sessionDir = createSessionDir("${BuildConfigWrap.APPLICATION_ID}_1.0_100")
            val result = module.findExistingSessionDir()
            result shouldBe sessionDir
        }

        @Test
        fun `returns most recent session by lastModified`() {
            val older = createSessionDir("${BuildConfigWrap.APPLICATION_ID}_1.0_100")
            older.setLastModified(1000L)
            File(older, "core.log").setLastModified(1000L)

            val newer = createSessionDir("${BuildConfigWrap.APPLICATION_ID}_1.0_200")
            newer.setLastModified(2000L)
            File(newer, "core.log").setLastModified(2000L)

            val result = module.findExistingSessionDir()
            result shouldBe newer
        }

        @Test
        fun `ignores dirs without core log`() {
            createSessionDir("${BuildConfigWrap.APPLICATION_ID}_1.0_100", withCoreLog = false)
            module.findExistingSessionDir().shouldBeNull()
        }

        @Test
        fun `ignores dirs with wrong prefix`() {
            createSessionDir("some.other.app_1.0_100")
            module.findExistingSessionDir().shouldBeNull()
        }

        @Test
        fun `returns null when no session dirs exist`() {
            module.findExistingSessionDir().shouldBeNull()
        }
    }

    @Nested
    inner class RoundTrip {
        @Test
        fun `write then read preserves data`() {
            val sessionDir = File(tempDir, "roundtrip-session")
            val startedAt = System.currentTimeMillis() - 5000
            module.writeTriggerFile(sessionDir, startedAt)

            val result = module.readTriggerFile()
            result.shouldNotBeNull()
            result.first shouldBe sessionDir
            result.second shouldBe startedAt
        }

        @Test
        fun `multiple restarts resume same session`() {
            val sessionDir = createSessionDir("eu.darken.octi_1.0_1709312400000")
            val startedAt = System.currentTimeMillis() - 300_000
            module.writeTriggerFile(sessionDir, startedAt)

            repeat(3) {
                val (resultDir, resultStartedAt) = module.findOrCreateSession()
                resultDir shouldBe sessionDir
                resultStartedAt shouldBe startedAt
            }
        }
    }
}
