package eu.darken.octi.modules.power.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class BatteryWidgetProviderInfoTest : BaseTest() {

    private val xml: String by lazy { File("src/main/res/xml/battery_widget_info.xml").readText() }

    @Test
    fun `widgetFeatures includes configuration_optional`() {
        val features = WIDGET_FEATURES_REGEX.find(xml)
            ?.groupValues?.get(1)
            ?.split("|")
            ?.map { it.trim() }
            ?.toSet()
        (features?.contains("configuration_optional") ?: false) shouldBe true
    }

    @Test
    fun `minHeight fits at least one row so initial placement is usable`() {
        // One row needs 8dp + 30dp + 8dp = 46dp. Anything below silently clips the bar.
        attributeDp("minHeight")!! shouldBe 50
    }

    @Test
    fun `minWidth allows a readable bar after BAR_HORIZONTAL_OVERHEAD`() {
        // Bar overhead is 64dp; we want at least ~115dp of bar width so the device
        // label and percent text fit. 180dp leaves 116dp.
        attributeDp("minWidth")!! shouldBe 180
    }

    @Test
    fun `minResize values match the floor of useful sizes`() {
        attributeDp("minResizeWidth")!! shouldBe 180
        attributeDp("minResizeHeight")!! shouldBe 50
    }

    @Test
    fun `targetCell defaults match sibling widgets so initial placement shows multiple devices`() {
        attributeInt("targetCellWidth")!! shouldBe 3
        attributeInt("targetCellHeight")!! shouldBe 2
    }

    private fun attributeDp(name: String): Int? {
        val match = Regex("""android:$name="(\d+)dp"""").find(xml) ?: return null
        return match.groupValues[1].toInt()
    }

    private fun attributeInt(name: String): Int? {
        val match = Regex("""android:$name="(\d+)"""").find(xml) ?: return null
        return match.groupValues[1].toInt()
    }

    companion object {
        private val WIDGET_FEATURES_REGEX = Regex("""android:widgetFeatures="([^"]+)"""")
    }
}
