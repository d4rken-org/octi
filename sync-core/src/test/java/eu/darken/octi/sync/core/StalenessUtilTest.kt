package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class StalenessUtilTest : BaseTest() {

    private val now = Instant.parse("2026-03-15T12:00:00Z")

    @Nested
    inner class `staleness boundary` {

        @Test
        fun `a device without any data is never stale`() {
            StalenessUtil.isStale(null, now) shouldBe false
        }

        @Test
        fun `six days old is fresh`() {
            StalenessUtil.isStale(now - 6.days, now) shouldBe false
        }

        @Test
        fun `just under seven days is fresh`() {
            StalenessUtil.isStale(now - 6.days - 23.hours, now) shouldBe false
        }

        @Test
        fun `exactly seven days is stale`() {
            StalenessUtil.isStale(now - 7.days, now) shouldBe true
        }

        @Test
        fun `just over seven days is stale`() {
            StalenessUtil.isStale(now - 7.days - 1.hours, now) shouldBe true
        }

        @Test
        fun `eight days old is stale`() {
            StalenessUtil.isStale(now - 8.days, now) shouldBe true
        }
    }

    @Nested
    inner class `system clock delegation` {

        @Test
        fun `the public overload uses the system clock`() {
            StalenessUtil.isStale(Clock.System.now() - 8.days) shouldBe true
            StalenessUtil.isStale(Clock.System.now() - 1.days) shouldBe false
        }
    }
}
