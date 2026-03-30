package eu.darken.octi.sync.core

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Duration
import java.time.Instant

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
    ) = ClockAnalyzer.analyze(devices, currentDevice, clockOffsets.map { it.asClockOffset() }, Duration.ofSeconds(thresholdSeconds), now)

    @Nested
    inner class `no discrepancy` {

        @Test
        fun `all timestamps in past - returns null`() {
            val devices = listOf(
                currentDevice to now.minusSeconds(60),
                deviceA to now.minusSeconds(30),
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
                deviceA to now.plusSeconds(60), // 1 min ahead, threshold is 2 min
            )
            analyze(devices).shouldBeNull()
        }

        @Test
        fun `skew exactly at threshold - returns null`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(120), // exactly at 2 min threshold
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
                deviceA to now.plusSeconds(300), // 5 min ahead
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
                deviceA to now.plusSeconds(300), // skewed
                deviceB to now.minusSeconds(10), // fine
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
                deviceA to now.plusSeconds(300),
                deviceB to now.plusSeconds(280),
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
                deviceA to now.plusSeconds(300),
                deviceB to now.plusSeconds(400),
                deviceC to now.plusSeconds(350),
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
                deviceA to now.plusSeconds(300),
            )
            // Local is 300s behind server (negative offset)
            val offsets = listOf(Duration.ofSeconds(-300))
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe true
            result.suspectDeviceIds shouldBe emptySet()
            result.localClockOffset shouldBe Duration.ofSeconds(-300)
        }

        @Test
        fun `local clock matches server - remote device is suspect`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(300),
            )
            // Local is only 10s off server (within threshold)
            val offsets = listOf(Duration.ofSeconds(-10))
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe false
            result.suspectDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `multiple offsets - uses median`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(300),
            )
            val offsets = listOf(
                Duration.ofSeconds(-290),
                Duration.ofSeconds(-310),
                Duration.ofSeconds(-300),
            )
            val result = analyze(devices, offsets)!!
            result.isCurrentDeviceSuspect shouldBe true
            // Sorted: [-310, -300, -290], median is -300
            result.localClockOffset shouldBe Duration.ofSeconds(-300)
        }

        @Test
        fun `offset overrides heuristic - 3 devices, 1 skewed but offset says local wrong`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(300),
                deviceB to now.minusSeconds(10),
            )
            // Heuristic would say deviceA is suspect, but offset says local is wrong
            val offsets = listOf(Duration.ofSeconds(-300))
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
                deviceA to now.plusSeconds(200),
            )
            analyze(devices, thresholdSeconds = 300).shouldBeNull()
        }

        @Test
        fun `custom threshold 300s - skew of 400s detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(400),
            )
            val result = analyze(devices, thresholdSeconds = 300)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `threshold 0 coerced to 1 - skew of 2s detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(2),
            )
            val result = analyze(devices, thresholdSeconds = 0)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `skew one second past threshold - detected`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(121),
            )
            val result = analyze(devices)!!
            result.skewedDeviceIds shouldBe setOf(deviceA)
        }

        @Test
        fun `extreme threshold coerced to max 86400`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(90000),
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
                deviceA to now.plusSeconds(300),
                otherDevice to now.minusSeconds(10),
            )
            val result = analyze(devices)!!
            result.canDetermineSource shouldBe false
            result.suspectDeviceIds shouldBe emptySet()
        }

        @Test
        fun `current device absent but clock offset available - can still determine`() {
            val devices = listOf(
                deviceA to now.plusSeconds(300),
                deviceB to now.minusSeconds(10),
            )
            // Local only 5s off server (within threshold)
            val offsets = listOf(Duration.ofSeconds(-5))
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
                deviceA to now.minusSeconds(1800),
            )
            // Server says local is +1800s ahead
            val offsets = listOf(Duration.ofSeconds(1800))
            val result = analyze(devices, offsets)!!
            result.canDetermineSource shouldBe true
            result.isCurrentDeviceSuspect shouldBe true
            result.suspectDeviceIds shouldBe emptySet()
            result.skewedDeviceIds shouldBe emptySet()
            result.localClockOffset shouldBe Duration.ofSeconds(1800)
        }

        @Test
        fun `clock ahead without offset - returns null`() {
            // Local clock is 30 min ahead — remote timestamps appear normal/past
            // No server offset available → can't detect
            val devices = listOf(
                currentDevice to now,
                deviceA to now.minusSeconds(1800),
            )
            analyze(devices).shouldBeNull()
        }

        @Test
        fun `clock slightly ahead within threshold - returns null`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.minusSeconds(60),
            )
            // Offset is within threshold
            val offsets = listOf(Duration.ofSeconds(60))
            analyze(devices, offsets).shouldBeNull()
        }
    }

    @Nested
    inner class `even offset list median` {

        @Test
        fun `2 offsets - uses average of both`() {
            val devices = listOf(
                currentDevice to now,
                deviceA to now.plusSeconds(300),
            )
            val offsets = listOf(
                Duration.ofSeconds(-280),
                Duration.ofSeconds(-320),
            )
            val result = analyze(devices, offsets)!!
            // Average of -320 and -280 = -300
            result.localClockOffset shouldBe Duration.ofSeconds(-300)
        }
    }
}
