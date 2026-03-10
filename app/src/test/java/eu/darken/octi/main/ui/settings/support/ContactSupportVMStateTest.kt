package eu.darken.octi.main.ui.settings.support

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ContactSupportVMStateTest : BaseTest() {

    private fun words(count: Int): String = (1..count).joinToString(" ") { "word$it" }

    @Nested
    inner class DescriptionWordCount {
        @Test
        fun `empty string returns 0`() {
            ContactSupportVM.State(description = "").descriptionWordCount shouldBe 0
        }

        @Test
        fun `whitespace only returns 0`() {
            ContactSupportVM.State(description = "   ").descriptionWordCount shouldBe 0
        }

        @Test
        fun `single word returns 1`() {
            ContactSupportVM.State(description = "hello").descriptionWordCount shouldBe 1
        }

        @Test
        fun `multiple words returns correct count`() {
            ContactSupportVM.State(description = "one two three").descriptionWordCount shouldBe 3
        }

        @Test
        fun `multiple spaces between words returns correct count`() {
            ContactSupportVM.State(description = "one   two   three").descriptionWordCount shouldBe 3
        }

        @Test
        fun `newlines and tabs count as word separators`() {
            ContactSupportVM.State(description = "one\ntwo\tthree").descriptionWordCount shouldBe 3
        }
    }

    @Nested
    inner class IsSendEnabled {
        @Test
        fun `19 description words is not enough`() {
            ContactSupportVM.State(description = words(19)).isSendEnabled shouldBe false
        }

        @Test
        fun `20 description words with non-bug category is enough`() {
            ContactSupportVM.State(
                category = ContactSupportVM.Category.QUESTION,
                description = words(20),
            ).isSendEnabled shouldBe true
        }

        @Test
        fun `BUG_REPORT with 20 desc words and 9 expected words is not enough`() {
            ContactSupportVM.State(
                category = ContactSupportVM.Category.BUG_REPORT,
                description = words(20),
                expectedBehavior = words(9),
            ).isSendEnabled shouldBe false
        }

        @Test
        fun `BUG_REPORT with 20 desc words and 10 expected words is enough`() {
            ContactSupportVM.State(
                category = ContactSupportVM.Category.BUG_REPORT,
                description = words(20),
                expectedBehavior = words(10),
            ).isSendEnabled shouldBe true
        }

        @Test
        fun `FEATURE_REQUEST ignores expectedBehavior`() {
            ContactSupportVM.State(
                category = ContactSupportVM.Category.FEATURE_REQUEST,
                description = words(20),
                expectedBehavior = "",
            ).isSendEnabled shouldBe true
        }

        @Test
        fun `isSending true disables send`() {
            ContactSupportVM.State(
                description = words(20),
                isSending = true,
            ).isSendEnabled shouldBe false
        }

        @Test
        fun `isRecording true disables send`() {
            ContactSupportVM.State(
                description = words(20),
                isRecording = true,
            ).isSendEnabled shouldBe false
        }
    }
}
