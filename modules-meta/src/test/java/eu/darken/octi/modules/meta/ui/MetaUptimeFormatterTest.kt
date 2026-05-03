package eu.darken.octi.modules.meta.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class MetaUptimeFormatterTest : BaseTest() {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Nested
    inner class `sub-minute durations` {
        @Test
        fun `future boot time renders as less than a minute`() {
            formatUptime(now, now + 5.minutes) shouldBe "<1m"
        }

        @Test
        fun `30 seconds uptime renders as less than a minute`() {
            formatUptime(now, now - 30.seconds) shouldBe "<1m"
        }
    }

    @Nested
    inner class `minute and hour ranges` {
        @Test
        fun `exactly one minute uptime`() {
            formatUptime(now, now - 1.minutes) shouldBe "1m"
        }

        @Test
        fun `47 minutes uptime`() {
            formatUptime(now, now - 47.minutes) shouldBe "47m"
        }

        @Test
        fun `exactly one hour uptime`() {
            formatUptime(now, now - 1.hours) shouldBe "1h 0m"
        }

        @Test
        fun `2 hours 30 minutes uptime`() {
            formatUptime(now, now - (2.hours + 30.minutes)) shouldBe "2h 30m"
        }
    }

    @Nested
    inner class `day range` {
        @Test
        fun `exactly one day uptime`() {
            formatUptime(now, now - 1.days) shouldBe "1d 0h"
        }

        @Test
        fun `5 days 12 hours uptime`() {
            formatUptime(now, now - (5.days + 12.hours)) shouldBe "5d 12h"
        }

        @Test
        fun `365 days uptime does not overflow`() {
            formatUptime(now, now - 365.days) shouldBe "365d 0h"
        }
    }
}
