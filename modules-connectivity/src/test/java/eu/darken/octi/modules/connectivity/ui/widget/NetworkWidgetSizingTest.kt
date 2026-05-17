package eu.darken.octi.modules.connectivity.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class NetworkWidgetSizingTest : BaseTest() {

    @Nested
    inner class `shouldUseTwoColumn` {

        @Test
        fun `narrow widget never uses two-column regardless of height`() {
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 80f, heightDp = 500f) shouldBe false
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 219f, heightDp = 500f) shouldBe false
        }

        @Test
        fun `wide-short widget falls back to compact rows so 76dp tiles don't clip`() {
            // Regression: 220dp x 50dp on a 4x1 cover-screen cell previously chose two-column
            // and clipped the 76dp tile vertically. Must use compact layout.
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 220f, heightDp = 50f) shouldBe false
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 300f, heightDp = 80f) shouldBe false
        }

        @Test
        fun `wide widget at tile-fit height uses two-column`() {
            // 2 * OUTER_PADDING + TILE_HEIGHT = 16 + 76 = 92dp
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 220f, heightDp = 92f) shouldBe true
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 220f, heightDp = 200f) shouldBe true
        }

        @Test
        fun `invalid height treats wide widget as two-column`() {
            // SizeMode.Exact always provides a finite height in practice; the fallback exists
            // for previews and unexpected zero-height states. Bias to the wide-layout intent.
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 220f, heightDp = 0f) shouldBe true
            NetworkWidgetSizing.shouldUseTwoColumn(widthDp = 220f, heightDp = Float.NaN) shouldBe true
        }
    }

    @Nested
    inner class `maxItemsForSize` {

        @Test
        fun `small cover-screen height still shows one compact row`() {
            NetworkWidgetSizing.maxItemsForSize(widthDp = 80f, heightDp = 50f) shouldBe 1
        }

        @Test
        fun `invalid height falls back to one visible slot`() {
            NetworkWidgetSizing.maxItemsForSize(widthDp = 80f, heightDp = 0f) shouldBe 1
            NetworkWidgetSizing.maxItemsForSize(widthDp = 80f, heightDp = Float.NaN) shouldBe 1
        }

        @Test
        fun `two column mode counts two tiles per fitted row`() {
            NetworkWidgetSizing.maxItemsForSize(widthDp = 220f, heightDp = 92f) shouldBe 2
            NetworkWidgetSizing.maxItemsForSize(widthDp = 220f, heightDp = 172f) shouldBe 4
        }

        @Test
        fun `wide-short widget uses compact layout, not two-column`() {
            // 220x50dp picks single-column compact rows. With INTER_ROW_SPACER=2 and
            // SINGLE_ROW_HEIGHT=28, the math fits one row.
            NetworkWidgetSizing.maxItemsForSize(widthDp = 220f, heightDp = 50f) shouldBe 1
        }

        @Test
        fun `visible item count is capped`() {
            NetworkWidgetSizing.maxItemsForSize(widthDp = 80f, heightDp = 10_000f) shouldBe
                NetworkWidgetSizing.MAX_VISIBLE_ITEMS
        }
    }

    @Nested
    inner class `computeVisibleSlots` {

        @Test
        fun `all devices fit when count is at or below max items`() {
            NetworkWidgetSizing.computeVisibleSlots(totalItemCount = 3, maxItems = 5) shouldBe
                NetworkWidgetSizing.VisibleSlots(visibleItemCount = 3, showOverflow = false)
        }

        @Test
        fun `overflow reserves one item slot for the more row`() {
            NetworkWidgetSizing.computeVisibleSlots(totalItemCount = 5, maxItems = 3) shouldBe
                NetworkWidgetSizing.VisibleSlots(visibleItemCount = 2, showOverflow = true)
        }

        @Test
        fun `single item slot shows overflow instead of silently truncating`() {
            NetworkWidgetSizing.computeVisibleSlots(totalItemCount = 5, maxItems = 1) shouldBe
                NetworkWidgetSizing.VisibleSlots(visibleItemCount = 0, showOverflow = true)
        }

        @Test
        fun `max items are capped to Glance child limit before slot allocation`() {
            NetworkWidgetSizing.computeVisibleSlots(totalItemCount = 20, maxItems = 100) shouldBe
                NetworkWidgetSizing.VisibleSlots(visibleItemCount = 9, showOverflow = true)
        }
    }
}
