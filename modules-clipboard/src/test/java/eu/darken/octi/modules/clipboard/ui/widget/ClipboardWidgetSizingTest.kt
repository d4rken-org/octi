package eu.darken.octi.modules.clipboard.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ClipboardWidgetSizingTest : BaseTest() {

    @Nested
    inner class `maxRemoteRowsForHeight` {

        @Test
        fun `small cover-screen height keeps space for the self row`() {
            ClipboardWidgetSizing.maxRemoteRowsForHeight(50f) shouldBe 0
        }

        @Test
        fun `one remote row fits above cover-screen minimum when height allows it`() {
            ClipboardWidgetSizing.maxRemoteRowsForHeight(80f) shouldBe 1
        }

        @Test
        fun `invalid height falls back to one remote slot`() {
            ClipboardWidgetSizing.maxRemoteRowsForHeight(0f) shouldBe 1
            ClipboardWidgetSizing.maxRemoteRowsForHeight(Float.NaN) shouldBe 1
        }

        @Test
        fun `remote rows are capped to keep root column under Glance child limit`() {
            ClipboardWidgetSizing.maxRemoteRowsForHeight(10_000f) shouldBe ClipboardWidgetSizing.MAX_REMOTE_SLOTS
            (ClipboardWidgetSizing.MAX_REMOTE_SLOTS + 1 <= 10) shouldBe true
        }
    }

    @Nested
    inner class `computeRemoteSlots` {

        @Test
        fun `all remote devices fit when count is at or below max rows`() {
            ClipboardWidgetSizing.computeRemoteSlots(totalRemoteCount = 3, maxRemoteRows = 5) shouldBe
                ClipboardWidgetSizing.VisibleSlots(visibleRemoteCount = 3, showOverflow = false)
        }

        @Test
        fun `overflow reserves one remote slot for the more row`() {
            ClipboardWidgetSizing.computeRemoteSlots(totalRemoteCount = 5, maxRemoteRows = 3) shouldBe
                ClipboardWidgetSizing.VisibleSlots(visibleRemoteCount = 2, showOverflow = true)
        }

        @Test
        fun `single remote slot shows overflow instead of silently truncating`() {
            ClipboardWidgetSizing.computeRemoteSlots(totalRemoteCount = 5, maxRemoteRows = 1) shouldBe
                ClipboardWidgetSizing.VisibleSlots(visibleRemoteCount = 0, showOverflow = true)
        }

        @Test
        fun `max remote rows are capped to leave room for self row`() {
            ClipboardWidgetSizing.computeRemoteSlots(totalRemoteCount = 20, maxRemoteRows = 100) shouldBe
                ClipboardWidgetSizing.VisibleSlots(visibleRemoteCount = 8, showOverflow = true)
        }
    }
}
