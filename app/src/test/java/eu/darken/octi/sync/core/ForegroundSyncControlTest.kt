package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ForegroundSyncControlTest : BaseTest() {

    @Nested
    inner class `computeForegroundSyncActive predicate` {

        @Test
        fun `active only when all three inputs are true`() {
            computeForegroundSyncActive(foreground = true, syncEnabled = true, networkOk = true) shouldBe true
        }

        @Test
        fun `inactive when any input is false`() {
            computeForegroundSyncActive(foreground = false, syncEnabled = true, networkOk = true) shouldBe false
            computeForegroundSyncActive(foreground = true, syncEnabled = false, networkOk = true) shouldBe false
            computeForegroundSyncActive(foreground = true, syncEnabled = true, networkOk = false) shouldBe false
        }

        @Test
        fun `inactive for all-false combinations`() {
            computeForegroundSyncActive(foreground = false, syncEnabled = false, networkOk = false) shouldBe false
            computeForegroundSyncActive(foreground = true, syncEnabled = false, networkOk = false) shouldBe false
            computeForegroundSyncActive(foreground = false, syncEnabled = true, networkOk = false) shouldBe false
            computeForegroundSyncActive(foreground = false, syncEnabled = false, networkOk = true) shouldBe false
        }

        // Regression guard: the predicate must not depend on Pro status. Re-introducing an
        // isPro parameter would break this compile, since the call sites in production code
        // (and these tests) only pass the three inputs above.
        @Test
        fun `pro status is not part of the predicate signature`() {
            val expected: (Boolean, Boolean, Boolean) -> Boolean = ::computeForegroundSyncActive
            expected(true, true, true) shouldBe true
        }
    }
}
