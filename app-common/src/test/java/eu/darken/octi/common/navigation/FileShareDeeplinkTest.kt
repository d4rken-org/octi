package eu.darken.octi.common.navigation

import android.content.Intent
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class FileShareDeeplinkTest : BaseTest() {

    private fun intentOf(
        action: String? = FileShareDeeplink.ACTION,
        deviceId: String? = "device-abc",
    ): Intent = mockk {
        every { this@mockk.action } returns action
        every { getStringExtra(FileShareDeeplink.EXTRA_DEVICE_ID) } returns deviceId
    }

    @Nested
    inner class `parse deeplinks` {
        @Test
        fun `null intent returns null`() {
            FileShareDeeplink.parse(null).shouldBeNull()
        }

        @Test
        fun `wrong action returns null`() {
            val intent = intentOf(action = "android.intent.action.MAIN")
            FileShareDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `null action returns null`() {
            val intent = intentOf(action = null)
            FileShareDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `missing deviceId returns null`() {
            val intent = intentOf(deviceId = null)
            FileShareDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `blank deviceId returns null`() {
            val intent = intentOf(deviceId = "   ")
            FileShareDeeplink.parse(intent).shouldBeNull()
        }

        @Test
        fun `valid deeplink parses to OpenFileShare`() {
            val intent = intentOf(deviceId = "dev-1")
            FileShareDeeplink.parse(intent) shouldBe FileShareDeeplink.OpenFileShare(deviceId = "dev-1")
        }
    }
}
