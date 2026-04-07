package eu.darken.octi.sync.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ClockAnalyzerTest : BaseTest() {

    private val now = Instant.parse("2026-03-29T18:00:00Z")
    private val currentDevice = DeviceId("current")
    private val deviceA = DeviceId("deviceA")
    private val deviceB = DeviceId("deviceB")
    private val deviceC = DeviceId("deviceC")
    private val defaultThreshold = 120L // 2 minutes

    private fun Duration.asClockOffset() = SyncConnectorState.ClockOffset(offset = this, measuredAt = now)

    private fun analyze(
        devices: List<Pair<DeviceId, Instant>>,
        clockOffsets: List<Duration> = emptyList(),
        thresholdSeconds: Long = defaultThreshold,
    ) = ClockAnalyzer.analyze(devices, currentDevice, clockOffsets.map { it.asClockOffset() }, thresholdSeconds.seconds, now)

    @Nested
    inner class `no discrepancy` {

        @Test
        fun `all timestamps in past - returns null`() {
            val devices = listOf(
                currentDevice to now - 60.seconds,
                deviceA to now - 30.seconds,
            )
            analyze(devices).shouldBeNull()
        }

        @Test
        fun `single device - returns null`() {
            val devices = listOf(currentDevice to now)
            analyze(devices).shouldBeNull()
        }

        @Test
        fun `skew within threshold - returns null`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 60.seconds, // 1 min ahead, threshold is 2 min
            )
            analyze(devices).shouldBeNull()
        }

        @Test
        fun `skew exactly at threshold - returns null`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 120.seconds, // exactly at 2 min threshold
            )
            analyze(devices).shouldBeNull()
        }
    }

    @Nested
    inner class `2 devices without server time` {

        @Test
        fun `1 remote skewed - canDetermineSource is false`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds, // 5 min ahead
            )
            val result = analyze(devices)!!
            result.canDetermineSource shouldBe false
            result.isCurrentDeviceSuspect shouldBe false
            result.suspectDeviceIds shouldBe emptySet()
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }
    }

    @Nested
    inner class `3+ devices without server time` {

        @Test
        fun `1 remote skewed - suspects that device`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds, // skewed
                deviceB to now - 10.seconds, // fine
            )
            val result = analyze(devices)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe false
            result.suspectDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `2 remotes skewed - suspects current device`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
                deviceB to now + 280.seconds,
            )
            val result = analyze(devices)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe true
            result.suspectDeviceIds shouldBe emptySet()
        }

        @Test
        fun `all remotes skewed - suspects current device`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
                deviceB to now + 400.seconds,
                deviceC to now + 350.seconds,
            )
            val result = analyze(devices)!!
            result.isCurrentDeviceSuspect shouldBe true
            result.skewedDeviceIds shouldBe setOf(deviceA, deviceB, deviceC)
        }
    }

    @Nested
    inner class `with clock offset` {

        @Test
        fun `local clock behind server - current device suspect even with 2 devices`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
            )
            // Local is 300s behind server (negative offset)
            val offsets = listOf((-300).seconds)
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe true
            result.suspectDeviceIds shouldBe emptySet()
            result.localClockOffset shouldBe (-300).seconds
        }

        @Test
        fun `local clock matches server - remote device is suspect`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
            )
            // Local is only 10s off server (within threshold)
            val offsets = listOf((-10).seconds)
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe false
            result.suspectDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `multiple offsets - uses median`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
            )
            val offsets = listOf(
                (-290).seconds,
                (-310).seconds,
                (-300).seconds,
            )
            val result = analyze(devices, offsets)!!
            result.isCurrentDeviceSuspect shouldBe true
            // Sorted: [-310, -300, -290], median is -300
            result.localClockOffset shouldBe (-300).seconds
        }

        @Test
        fun `offset overrides heuristic - 3 devices, 1 skewed but offset says local wrong`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
                deviceB to now - 10.seconds,
            )
            // Heuristic would say deviceA is suspect, but offset says local is wrong
            val offsets = listOf((-300).seconds)
            val result = analyze(devices, offsets)!!
            result.isCurrentDeviceSuspect shouldBe true
            result.suspectDeviceIds shouldBe emptySet()
        }
    }

    @Nested
    inner class `threshold` {

        @Test
        fun `custom threshold 300s - skew of 200s not detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 200.seconds,
            )
            analyze(devices, thresholdSeconds = 300).shouldBeNull()
        }

        @Test
        fun `custom threshold 300s - skew of 400s detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 400.seconds,
            )
            val result = analyze(devices, thresholdSeconds = 300)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `threshold 0 coerced to 1 - skew of 2s detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 2.seconds,
            )
            val result = analyze(devices, thresholdSeconds = 0)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `skew one second past threshold - detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 121.seconds,
            )
            val result = analyze(devices)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `extreme threshold coerced to max 86400`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 90000.seconds,
            )
            val result = analyze(devices, thresholdSeconds = Long.MAX_VALUE)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }
    }

    @Nested
    inner class `current device absent` {

        @Test
        fun `current device not in sync data - canDetermineSource is false`() {
            val otherDevice = DeviceId("other")
            val devices = listOf(
                deviceA to now + 300.seconds,
                otherDevice to now - 10.seconds,
            )
            val result = analyze(devices)!!
            result.canDetermineSource shouldBe false
            result.suspectDeviceIds shouldBe emptySet()
        }

        @Test
        fun `current device absent but clock offset available - can still determine`() {
            val devices = listOf(
                deviceA to now + 300.seconds,
                deviceB to now - 10.seconds,
            )
            // Local only 5s off server (within threshold)
            val offsets = listOf((-5).seconds)
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.suspectDeviceIds shouldBe setOf(deviceA)
        }
    }

    @Nested
    inner class `local clock ahead` {

        @Test
        fun `clock ahead detected via positive offset - no future timestamps`() {
            // Local clock is 30 min ahead — remote timestamps appear normal/past
            val devices = listOf(
                currentDevice to now,
                deviceA to now - 1800.seconds,
            )
            // Server says local is +1800s ahead
            val offsets = listOf((1800).seconds)
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe true
            result.suspectDeviceIds shouldBe emptySet()
            result.skewedDeviceIds shouldBe emptySet()
            result.localClockOffset shouldBe (1800).seconds
        }

        @Test
        fun `clock ahead without offset - returns null`() {
            // Local clock is 30 min ahead — remote timestamps appear normal/past
            // No server offset available → can't detect
            val devices = listOf(
                currentDevice to now,
                deviceA to now - 1800.seconds,
            )
            analyze(devices).shouldBeNull()
        }

        @Test
        fun `clock slightly ahead within threshold - returns null`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now - 60.seconds,
            )
            // Offset is within threshold
            val offsets = listOf((60).seconds)
            analyze(devices, offsets).shouldBeNull()
        }
    }

    @Nested
    inner class `even offset list median` {

        @Test
        fun `2 offsets - uses average of both`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now + 300.seconds,
            )
            val offsets = listOf(
                (-280).seconds,
                (-320).seconds,
            )
            val result = analyze(devices, offsets)!!
            // Average of -320 and -280 = -300
            result.localClockOffset shouldBe (-300).seconds
        }
    }
}
