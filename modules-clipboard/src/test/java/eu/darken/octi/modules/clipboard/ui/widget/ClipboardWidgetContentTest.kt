package eu.darken.octi.modules.clipboard.ui.widget

import eu.darken.octi.modules.clipboard.ClipboardInfo
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ClipboardWidgetContentTest : BaseTest() {

    @Nested
    inner class `toWidgetPreview` {

        @Test
        fun `EMPTY type returns no copyable content and null text`() {
            val preview = ClipboardInfo().toWidgetPreview()
            preview.hasCopyableContent shouldBe false
            preview.text shouldBe null
        }

        @Test
        fun `SIMPLE_TEXT with empty bytes returns no copyable content`() {
            val info = ClipboardInfo(
                type = ClipboardInfo.Type.SIMPLE_TEXT,
                data = ByteString.EMPTY,
            )
            val preview = info.toWidgetPreview()
            preview.hasCopyableContent shouldBe false
            preview.text shouldBe null
        }

        @Test
        fun `SIMPLE_TEXT with content returns copyable content and full text`() {
            val info = ClipboardInfo(
                type = ClipboardInfo.Type.SIMPLE_TEXT,
                data = "Hello".encodeUtf8(),
            )
            val preview = info.toWidgetPreview()
            preview.hasCopyableContent shouldBe true
            preview.text shouldBe "Hello"
        }

        @Test
        fun `text longer than 40 chars is truncated with ellipsis`() {
            val longText = "a".repeat(100)
            val info = ClipboardInfo(
                type = ClipboardInfo.Type.SIMPLE_TEXT,
                data = longText.encodeUtf8(),
            )
            val preview = info.toWidgetPreview()
            preview.hasCopyableContent shouldBe true
            preview.text shouldBe "a".repeat(40) + "\u2026"
            preview.text!!.length shouldBe 41
        }

        @Test
        fun `text exactly 40 chars is not truncated`() {
            val text = "a".repeat(40)
            val info = ClipboardInfo(
                type = ClipboardInfo.Type.SIMPLE_TEXT,
                data = text.encodeUtf8(),
            )
            val preview = info.toWidgetPreview()
            preview.text shouldBe text
            preview.text!!.length shouldBe 40
        }
    }
}
