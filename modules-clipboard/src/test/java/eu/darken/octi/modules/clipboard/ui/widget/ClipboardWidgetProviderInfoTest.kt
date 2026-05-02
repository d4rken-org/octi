package eu.darken.octi.modules.clipboard.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class ClipboardWidgetProviderInfoTest : BaseTest() {

    @Test
    fun `widgetFeatures includes configuration_optional`() {
        val xml = File("src/main/res/xml/clipboard_widget_info.xml").readText()
        val features = WIDGET_FEATURES_REGEX.find(xml)
            ?.groupValues?.get(1)
            ?.split("|")
            ?.map { it.trim() }
            ?.toSet()
        (features?.contains("configuration_optional") ?: false) shouldBe true
    }

    companion object {
        private val WIDGET_FEATURES_REGEX = Regex("""android:widgetFeatures="([^"]+)"""")
    }
}
