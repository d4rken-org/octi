package eu.darken.octi.common.navigation

import android.content.Intent
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class WidgetDeeplinkTest : BaseTest() {

    private fun intentOf(
        action: String? = WidgetDeeplink.ACTION,
        deviceId: String? = "device-abc",
        moduleType: String? = "POWER",
    ): Intent = mockk {
        every { this@mockk.action } returns action
        every { getStringExtra(WidgetDeeplink.EXTRA_DEVICE_ID) } returns deviceId
        every { getStringExtra(WidgetDeeplink.EXTRA_MODULE_TYPE) } returns moduleType
    }

    @Nested
    inner class `parse deeplinks` {
        @Test
        fun `null intent returns null`() {
            WidgetDeeplink.parse(null).shouldBeNull()
        }

        @Test
        fun `wrong action returns null`() {
            val intent = intentOf(action = "android.intent.action.MAIN")
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `null action returns null`() {
            val intent = intentOf(action = null)
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `missing deviceId returns null`() {
            val intent = intentOf(deviceId = null)
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `blank deviceId returns null`() {
            val intent = intentOf(deviceId = "   ")
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `missing moduleType returns null`() {
            val intent = intentOf(moduleType = null)
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `unknown moduleType returns null`() {
            val intent = intentOf(moduleType = "UNKNOWN_MODULE")
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `lowercase moduleType returns null`() {
            // Enum name matching is case-sensitive.
            val intent = intentOf(moduleType = "power")
            WidgetDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `valid POWER deeplink is parsed`() {
            val intent = intentOf(deviceId = "dev-1", moduleType = "POWER")
            WidgetDeeplink.parse(intent) shouldBe WidgetDeeplink.OpenModuleDetail(
                deviceId = "dev-1",
                moduleType = WidgetDeeplink.ModuleType.POWER,
            )
        }

        @Test
        fun `valid CONNECTIVITY deeplink is parsed`() {
            val intent = intentOf(deviceId = "dev-2", moduleType = "CONNECTIVITY")
            WidgetDeeplink.parse(intent) shouldBe WidgetDeeplink.OpenModuleDetail(
                deviceId = "dev-2",
                moduleType = WidgetDeeplink.ModuleType.CONNECTIVITY,
            )
        }

        @Test
        fun `valid CLIPBOARD deeplink is parsed`() {
            val intent = intentOf(deviceId = "dev-3", moduleType = "CLIPBOARD")
            WidgetDeeplink.parse(intent) shouldBe WidgetDeeplink.OpenModuleDetail(
                deviceId = "dev-3",
                moduleType = WidgetDeeplink.ModuleType.CLIPBOARD,
            )
        }
    }

}
