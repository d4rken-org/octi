package eu.darken.octi.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class TextExtensionsTest : BaseTest() {

    @Nested
    inner class `humanizeIdentifier separators` {
        @Test
        fun `underscores become spaces`() {
            humanizeIdentifier("home_assistant") shouldBe "Home Assistant"
        }

        @Test
        fun `dashes become spaces`() {
            humanizeIdentifier("desktop-linux") shouldBe "Desktop Linux"
        }

        @Test
        fun `mixed separators`() {
            humanizeIdentifier("home_assistant-core") shouldBe "Home Assistant Core"
        }

        @Test
        fun `repeated separators collapse`() {
            humanizeIdentifier("home__assistant") shouldBe "Home Assistant"
        }

        @Test
        fun `leading and trailing separators are dropped`() {
            humanizeIdentifier("_home_assistant_") shouldBe "Home Assistant"
        }

        @Test
        fun `surrounding whitespace is trimmed`() {
            humanizeIdentifier("  home__assistant  ") shouldBe "Home Assistant"
        }

        @Test
        fun `internal whitespace runs collapse`() {
            humanizeIdentifier("chrome  os   flex") shouldBe "Chrome Os Flex"
        }

        @Test
        fun `tabs and newlines count as separators`() {
            humanizeIdentifier("home\tassistant\ncore") shouldBe "Home Assistant Core"
        }
    }

    @Nested
    inner class `humanizeIdentifier casing` {
        @Test
        fun `token tails are preserved`() {
            humanizeIdentifier("FreeBSD") shouldBe "FreeBSD"
        }

        @Test
        fun `all uppercase input stays uppercase`() {
            humanizeIdentifier("HOME_ASSISTANT") shouldBe "HOME ASSISTANT"
        }

        @Test
        fun `single lowercase token is capitalized`() {
            humanizeIdentifier("other") shouldBe "Other"
        }

        @Test
        fun `already capitalized token is unchanged`() {
            humanizeIdentifier("Linux") shouldBe "Linux"
        }

        @Test
        fun `non letter first character is left alone`() {
            humanizeIdentifier("3dprinter") shouldBe "3dprinter"
        }
    }

    @Nested
    inner class `humanizeIdentifier degenerate input` {
        @Test
        fun `blank input returns empty string`() {
            humanizeIdentifier("") shouldBe ""
        }

        @Test
        fun `whitespace only input returns empty string`() {
            humanizeIdentifier("   ") shouldBe ""
        }

        @Test
        fun `separator only input returns the trimmed input`() {
            humanizeIdentifier(" __ ") shouldBe "__"
        }
    }
}
