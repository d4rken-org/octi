package eu.darken.octi.modules.power.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BatteryWidgetSizingTest : BaseTest() {

    @Nested
    inner class `maxRowsForHeight` {

        @Test
        fun `non-positive height falls back to one visible slot`() {
            BatteryWidgetSizing.maxRowsForHeight(0f) shouldBe 1
            BatteryWidgetSizing.maxRowsForHeight(-1f) shouldBe 1
        }

        @Test
        fun `non-finite height falls back to one visible slot`() {
            BatteryWidgetSizing.maxRowsForHeight(Float.NaN) shouldBe 1
            BatteryWidgetSizing.maxRowsForHeight(Float.POSITIVE_INFINITY) shouldBe 1
        }

        @Test
        fun `tiny height returns at least one row so widget never renders blank`() {
            BatteryWidgetSizing.maxRowsForHeight(1f) shouldBe 1
            BatteryWidgetSizing.maxRowsForHeight(20f) shouldBe 1
            BatteryWidgetSizing.maxRowsForHeight(46f) shouldBe 1  // exact 1-row fit
            BatteryWidgetSizing.maxRowsForHeight(77f) shouldBe 1  // just below 2-row fit
        }

        @Test
        fun `exact fits return the corresponding row count`() {
            BatteryWidgetSizing.maxRowsForHeight(78f) shouldBe 2  // 32*2 + 14
            BatteryWidgetSizing.maxRowsForHeight(110f) shouldBe 3 // 32*3 + 14
            BatteryWidgetSizing.maxRowsForHeight(142f) shouldBe 4
            BatteryWidgetSizing.maxRowsForHeight(174f) shouldBe 5
        }

        @Test
        fun `heights between exact fits round down`() {
            BatteryWidgetSizing.maxRowsForHeight(109f) shouldBe 2 // not enough for 3
            BatteryWidgetSizing.maxRowsForHeight(141f) shouldBe 3
            BatteryWidgetSizing.maxRowsForHeight(173f) shouldBe 4
        }

        @Test
        fun `very tall widgets are capped to MAX_VISIBLE_ROWS to stay under Glance child limit`() {
            BatteryWidgetSizing.maxRowsForHeight(1000f) shouldBe BatteryWidgetSizing.MAX_VISIBLE_ROWS
            BatteryWidgetSizing.maxRowsForHeight(10_000f) shouldBe BatteryWidgetSizing.MAX_VISIBLE_ROWS
        }

        @Test
        fun `MAX_VISIBLE_ROWS stays under Glance Column 10-child cap`() {
            (BatteryWidgetSizing.MAX_VISIBLE_ROWS <= 10) shouldBe true
        }
    }

    @Nested
    inner class `computeVisibleSlots` {

        @Test
        fun `no devices means no slots and no overflow`() {
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 0, maxRows = 5) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 0, showOverflow = false)
        }

        @Test
        fun `zero maxRows shows nothing`() {
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 5, maxRows = 0) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 0, showOverflow = false)
        }

        @Test
        fun `all devices fit when count is at or below maxRows`() {
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 3, maxRows = 5) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 3, showOverflow = false)
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 5, maxRows = 5) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 5, showOverflow = false)
        }

        @Test
        fun `overflow reserves one slot for the +N more indicator`() {
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 5, maxRows = 3) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 2, showOverflow = true)
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 10, maxRows = 4) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 3, showOverflow = true)
        }

        @Test
        fun `maxRows of 1 with overflow uses the only slot for the indicator`() {
            // Silent truncation was the reported bug — at maxRows=1 we still surface that more exist.
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 5, maxRows = 1) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 0, showOverflow = true)
        }

        @Test
        fun `maxRows of 1 with exactly one device shows that device`() {
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 1, maxRows = 1) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 1, showOverflow = false)
        }

        @Test
        fun `maxRows is capped to Glance child limit before slot allocation`() {
            BatteryWidgetSizing.computeVisibleSlots(totalDeviceCount = 20, maxRows = 100) shouldBe
                BatteryWidgetSizing.VisibleSlots(visibleDeviceCount = 9, showOverflow = true)
        }
    }
}
