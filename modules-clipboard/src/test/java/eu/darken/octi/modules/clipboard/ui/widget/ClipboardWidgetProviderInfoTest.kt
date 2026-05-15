package eu.darken.octi.modules.clipboard.ui.widget

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.io.File

class ClipboardWidgetProviderInfoTest : BaseTest() {

    private val xml: String by lazy { File("src/main/res/xml/clipboard_widget_info.xml").readText() }

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
    fun `previewImage is declared for Android pre-12 widget pickers`() {
        // previewLayout is API 31+; on older Android the launcher's picker falls back to
        // previewImage. Without this, Android 9-11 users see a generic app-icon tile.
        PREVIEW_IMAGE_REGEX.containsMatchIn(xml) shouldBe true
    }

    companion object {
        private val WIDGET_FEATURES_REGEX = Regex("""android:widgetFeatures="([^"]+)"""")
        private val PREVIEW_IMAGE_REGEX = Regex("""android:previewImage="@drawable/[^"]+"""")
    }
}
