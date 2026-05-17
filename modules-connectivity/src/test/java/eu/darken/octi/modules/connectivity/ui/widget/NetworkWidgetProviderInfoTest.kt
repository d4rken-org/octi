package eu.darken.octi.modules.connectivity.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class NetworkWidgetProviderInfoTest : BaseTest() {

    private val xml: String by lazy { File("src/main/res/xml/network_widget_info.xml").readText() }

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
    fun `minimum dimensions stay cover-screen friendly`() {
        attributeDp("minWidth")!! shouldBe 80
        attributeDp("minHeight")!! shouldBe 50
        attributeDp("minResizeWidth")!! shouldBe 80
        attributeDp("minResizeHeight")!! shouldBe 50
    }

    @Test
    fun `targetCell defaults fit constrained cover-screen pickers`() {
        attributeInt("targetCellWidth")!! shouldBe 2
        attributeInt("targetCellHeight")!! shouldBe 1
    }

    @Test
    fun `previewImage is declared for Android pre-12 widget pickers`() {
        // previewLayout is API 31+; on older Android the launcher's picker falls back to
        // previewImage. Without this, Android 9-11 users see a generic app-icon tile.
        PREVIEW_IMAGE_REGEX.containsMatchIn(xml) shouldBe true
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
        private val PREVIEW_IMAGE_REGEX = Regex("""android:previewImage="@drawable/[^"]+"""")
    }
}
