package eu.darken.octi.modules.power.core

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Instant

class PowerInfoEquivalenceTest : BaseTest() {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun info(
        status: PowerInfo.Status = PowerInfo.Status.CHARGING,
        level: Int = 80,
        scale: Int = 100,
        health: Int? = 2,
        temp: Float? = 30.0f,
        currentNow: Int? = -500_000,
        currenAvg: Int? = -500_000,
        fullSince: Instant? = t0,
        fullAt: Instant? = t0,
        emptyAt: Instant? = null,
    ) = PowerInfo(
        status = status,
        battery = PowerInfo.Battery(level = level, scale = scale, health = health, temp = temp),
        chargeIO = PowerInfo.ChargeIO(
            currentNow = currentNow,
            currenAvg = currenAvg,
            fullSince = fullSince,
            fullAt = fullAt,
            emptyAt = emptyAt,
        ),
    )

    @Test fun `identical infos are equivalent`() {
        info().isEquivalentForSync(info()) shouldBe true
    }

    @Test fun `status change is significant`() {
        info(status = PowerInfo.Status.CHARGING)
            .isEquivalentForSync(info(status = PowerInfo.Status.DISCHARGING)) shouldBe false
    }

    @Test fun `battery level or health change is significant`() {
        info(level = 80).isEquivalentForSync(info(level = 81)) shouldBe false
        info(health = 2).isEquivalentForSync(info(health = 3)) shouldBe false
    }

    @Test fun `temperature within tolerance is noise`() {
        info(temp = 30.0f).isEquivalentForSync(info(temp = 30.5f)) shouldBe true
        info(temp = 30.0f).isEquivalentForSync(info(temp = 31.0f)) shouldBe false
    }

    @Test fun `current within tolerance is noise`() {
        info(currentNow = -500_000).isEquivalentForSync(info(currentNow = -520_000)) shouldBe true
        info(currentNow = -500_000).isEquivalentForSync(info(currentNow = -560_000)) shouldBe false
    }

    @Test fun `current avg within tolerance is noise`() {
        info(currenAvg = -500_000).isEquivalentForSync(info(currenAvg = -540_000)) shouldBe true
        info(currenAvg = -500_000).isEquivalentForSync(info(currenAvg = -600_000)) shouldBe false
    }

    @Test fun `charge ETA within a minute is noise`() {
        info(fullAt = t0).isEquivalentForSync(info(fullAt = t0.plusSeconds(45))) shouldBe true
        info(fullAt = t0).isEquivalentForSync(info(fullAt = t0.plusSeconds(90))) shouldBe false
    }

    @Test fun `crossing a charge-speed threshold is significant even within current tolerance`() {
        // 980 mA (SLOW) vs 1020 mA (NORMAL): 40 mA apart (under tolerance) but different speed category.
        info(currentNow = 980_000).isEquivalentForSync(info(currentNow = 1_020_000)) shouldBe false
        // Same side of the threshold, within tolerance -> still noise.
        info(currentNow = 1_020_000).isEquivalentForSync(info(currentNow = 1_040_000)) shouldBe true
    }

    @Test fun `crossing the NORMAL to FAST threshold is significant even within current tolerance`() {
        // 2480 mA (NORMAL) vs 2520 mA (FAST): 40 mA apart (under tolerance) but crosses the 2.5 A line.
        info(currentNow = 2_480_000).isEquivalentForSync(info(currentNow = 2_520_000)) shouldBe false
        // Both FAST, within tolerance -> noise.
        info(currentNow = 2_520_000).isEquivalentForSync(info(currentNow = 2_540_000)) shouldBe true
    }

    @Test fun `battery scale change is significant`() {
        info(scale = 100).isEquivalentForSync(info(scale = 200)) shouldBe false
    }

    @Test fun `fullSince within tolerance is noise, beyond is significant`() {
        info(fullSince = t0).isEquivalentForSync(info(fullSince = t0.plusSeconds(45))) shouldBe true
        info(fullSince = t0).isEquivalentForSync(info(fullSince = t0.plusSeconds(90))) shouldBe false
    }

    @Test fun `emptyAt within tolerance is noise, beyond is significant`() {
        info(emptyAt = t0).isEquivalentForSync(info(emptyAt = t0.plusSeconds(45))) shouldBe true
        info(emptyAt = t0).isEquivalentForSync(info(emptyAt = t0.plusSeconds(90))) shouldBe false
    }

    @Test fun `null transitions are always significant`() {
        info(currentNow = -500_000).isEquivalentForSync(info(currentNow = null)) shouldBe false
        info(currentNow = null).isEquivalentForSync(info(currentNow = null)) shouldBe true
        info(temp = null).isEquivalentForSync(info(temp = 30.0f)) shouldBe false
        info(fullAt = null).isEquivalentForSync(info(fullAt = null)) shouldBe true
    }

    @Test fun `distinctUntilChanged collapses sub-tolerance jitter but not real change`() = runTest2 {
        // Current jitters within ±50 mA and ETA drifts a few seconds — all noise — then a real jump.
        val samples = listOf(
            info(currentNow = -500_000, fullAt = t0),
            info(currentNow = -515_000, fullAt = t0.plusSeconds(5)),
            info(currentNow = -488_000, fullAt = t0.plusSeconds(10)),
            info(currentNow = -502_000, fullAt = t0.plusSeconds(15)),
            info(currentNow = -900_000, fullAt = t0.plusSeconds(15)), // real change (>50 mA)
        )

        val emitted = flowOf(*samples.toTypedArray())
            .distinctUntilChanged { old, new -> old.isEquivalentForSync(new) }
            .toList()

        // First sample + the real change only — the three noisy samples in between are suppressed.
        emitted.size shouldBe 2
        emitted.first().chargeIO.currentNow shouldBe -500_000
        emitted.last().chargeIO.currentNow shouldBe -900_000
    }

    @Test fun `slow ramp still eventually emits (hysteresis vs last emitted)`() = runTest2 {
        // Each step is <50 mA but they accumulate; compared against the last EMITTED value, the ramp
        // must emit again once cumulative drift crosses the tolerance — not stay suppressed forever.
        val samples = (0..5).map { info(currentNow = -500_000 - it * 20_000) } // -500k, -520k, ... -600k

        val emitted = flowOf(*samples.toTypedArray())
            .distinctUntilChanged { old, new -> old.isEquivalentForSync(new) }
            .toList()

        // -500k emits; -520k noise; -540k is 40k from -500k -> still noise; -560k is 60k -> emits;
        // -580k noise; -600k is 40k from -560k -> noise. => 2 emissions.
        emitted.map { it.chargeIO.currentNow } shouldBe listOf(-500_000, -560_000)
    }

    private fun Instant.plusSeconds(s: Long) = Instant.fromEpochMilliseconds(toEpochMilliseconds() + s * 1000)
}
