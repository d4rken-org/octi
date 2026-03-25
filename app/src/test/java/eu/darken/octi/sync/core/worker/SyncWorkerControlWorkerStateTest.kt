package eu.darken.octi.sync.core.worker

import androidx.work.WorkInfo
import eu.darken.octi.sync.core.worker.SyncWorkerControl.Companion.toWorkerInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class SyncWorkerControlWorkerStateTest : BaseTest() {

    private fun mockWorkInfo(
        state: WorkInfo.State,
        nextScheduleTimeMillis: Long = Long.MAX_VALUE,
    ): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { this@mockk.nextScheduleTimeMillis } returns nextScheduleTimeMillis
    }

    @Nested
    inner class `toWorkerInfo mapping` {
        @Test
        fun `null WorkInfo returns disabled state`() {
            val info: WorkInfo? = null
            val result = info.toWorkerInfo()

            result.isEnabled shouldBe false
            result.isRunning shouldBe false
            result.isBlocked shouldBe false
            result.nextRunAt shouldBe null
        }

        @Test
        fun `BLOCKED worker is blocked but not enabled`() {
            val result = mockWorkInfo(WorkInfo.State.BLOCKED).toWorkerInfo()

            result.isEnabled shouldBe false
            result.isRunning shouldBe false
            result.isBlocked shouldBe true
        }

        @Test
        fun `ENQUEUED worker is enabled but not running`() {
            val result = mockWorkInfo(WorkInfo.State.ENQUEUED).toWorkerInfo()

            result.isEnabled shouldBe true
            result.isRunning shouldBe false
        }

        @Test
        fun `RUNNING worker is enabled and running`() {
            val result = mockWorkInfo(WorkInfo.State.RUNNING).toWorkerInfo()

            result.isEnabled shouldBe true
            result.isRunning shouldBe true
        }

        @Test
        fun `CANCELLED worker is not enabled`() {
            val result = mockWorkInfo(WorkInfo.State.CANCELLED).toWorkerInfo()

            result.isEnabled shouldBe false
            result.isRunning shouldBe false
        }

        @Test
        fun `SUCCEEDED worker is not enabled`() {
            val result = mockWorkInfo(WorkInfo.State.SUCCEEDED).toWorkerInfo()

            result.isEnabled shouldBe false
            result.isRunning shouldBe false
        }

        @Test
        fun `valid next schedule time converts to Instant`() {
            val epochMs = Instant.now().plusSeconds(3600).toEpochMilli()
            val result = mockWorkInfo(
                state = WorkInfo.State.ENQUEUED,
                nextScheduleTimeMillis = epochMs,
            ).toWorkerInfo()

            result.nextRunAt shouldBe Instant.ofEpochMilli(epochMs)
        }

        @Test
        fun `MAX_VALUE next schedule time returns null`() {
            val result = mockWorkInfo(
                state = WorkInfo.State.ENQUEUED,
                nextScheduleTimeMillis = Long.MAX_VALUE,
            ).toWorkerInfo()

            result.nextRunAt shouldBe null
        }

        @Test
        fun `zero next schedule time returns null`() {
            val result = mockWorkInfo(
                state = WorkInfo.State.ENQUEUED,
                nextScheduleTimeMillis = 0,
            ).toWorkerInfo()

            result.nextRunAt shouldBe null
        }
    }
}
