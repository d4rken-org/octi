package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class StalenessUtilTest : BaseTest() {

    @Nested
    inner class `hasClockSkew` {

        @Test
        fun `null returns false`() {
            StalenessUtil.hasClockSkew(null) shouldBe false
        }

        @Test
        fun `past timestamp returns false`() {
            val pastTime = Instant.now().minusSeconds(3600)
            StalenessUtil.hasClockSkew(pastTime) shouldBe false
        }

        @Test
        fun `timestamp 1 minute in future returns false - below threshold`() {
            val nearFuture = Instant.now().plusSeconds(60)
            StalenessUtil.hasClockSkew(nearFuture) shouldBe false
        }

        @Test
        fun `timestamp 5 minutes in future returns true - above threshold`() {
            val future = Instant.now().plusSeconds(300)
            StalenessUtil.hasClockSkew(future) shouldBe true
        }

        @Test
        fun `timestamp exactly at threshold returns false`() {
            val atThreshold = Instant.now().plusSeconds(120)
            StalenessUtil.hasClockSkew(atThreshold) shouldBe false
        }

        @Test
        fun `timestamp 1 hour in future returns true`() {
            val farFuture = Instant.now().plusSeconds(3600)
            StalenessUtil.hasClockSkew(farFuture) shouldBe true
        }
    }
}
