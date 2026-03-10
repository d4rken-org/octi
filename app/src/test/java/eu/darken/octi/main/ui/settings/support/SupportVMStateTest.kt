package eu.darken.octi.main.ui.settings.support

import eu.darken.octi.common.debug.recording.core.DebugSession
import eu.darken.octi.common.debug.recording.core.LogSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class SupportVMStateTest : BaseTest() {

    private fun readySession(size: Long = 1000L) = DebugSession.Ready(
        session = LogSession(File("test-ready")),
        size = size,
        lastModified = 0L,
    )

    private fun recordingSession(size: Long = 500L) = DebugSession.Recording(
        session = LogSession(File("test-recording")),
        size = size,
        lastModified = 0L,
        startedAt = 0L,
    )

    private fun compressingSession(size: Long = 800L) = DebugSession.Compressing(
        session = LogSession(File("test-compressing")),
        size = size,
        lastModified = 0L,
    )

    @Nested
    inner class SessionCount {
        @Test
        fun `empty list returns 0`() {
            SupportVM.State().sessionCount shouldBe 0
        }

        @Test
        fun `counts all session types including recording`() {
            SupportVM.State(
                debugSessions = listOf(readySession(), recordingSession(), compressingSession()),
            ).sessionCount shouldBe 3
        }

        @Test
        fun `multiple sessions returns correct count`() {
            SupportVM.State(
                debugSessions = listOf(readySession(), readySession()),
            ).sessionCount shouldBe 2
        }
    }

    @Nested
    inner class TotalLogSize {
        @Test
        fun `empty list returns 0`() {
            SupportVM.State().totalLogSize shouldBe 0L
        }

        @Test
        fun `sums all session sizes`() {
            SupportVM.State(
                debugSessions = listOf(readySession(100L), readySession(200L)),
            ).totalLogSize shouldBe 300L
        }

        @Test
        fun `includes all session types in sum`() {
            SupportVM.State(
                debugSessions = listOf(
                    readySession(1000L),
                    recordingSession(500L),
                    compressingSession(800L),
                ),
            ).totalLogSize shouldBe 2300L
        }
    }
}
