package eu.darken.octi.sync.ui.devices

import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DeviceItemTest : BaseTest() {

    private val deviceId = DeviceId("test-device-123")

    private fun item(
        metaInfo: MetaInfo? = null,
        error: Exception? = null,
        serverAddedAt: Instant? = null,
    ) = SyncDevicesVM.DeviceItem(
        deviceId = deviceId,
        metaInfo = metaInfo,
        lastSeen = null,
        error = error,
        serverVersion = null,
        serverAddedAt = serverAddedAt,
    )

    private fun metaInfo() = MetaInfo(
        deviceId = deviceId,
        deviceLabel = "Test",
        octiVersionName = "0.2.0",
        octiGitSha = "abc",
        deviceManufacturer = "Google",
        deviceName = "Pixel",
        deviceType = MetaInfo.DeviceType.PHONE,
        deviceBootedAt = Instant.now(),
        androidVersionName = "14",
        androidApiLevel = 34,
        androidSecurityPatch = "2024-01-01",
    )

    @Nested
    inner class `hasNoModuleData` {
        @Test
        fun `true when metaInfo and error are both null`() {
            item().hasNoModuleData shouldBe true
        }

        @Test
        fun `false when metaInfo is present`() {
            item(metaInfo = metaInfo()).hasNoModuleData shouldBe false
        }

        @Test
        fun `false when error is present`() {
            item(error = RuntimeException("decrypt failed")).hasNoModuleData shouldBe false
        }
    }

    @Nested
    inner class `isEncryptionIncompatible` {
        @Test
        fun `true when no module data, GCM-SIV account, added over 2 minutes ago`() {
            val old = Instant.now().minusSeconds(300)
            item(serverAddedAt = old).isEncryptionIncompatible("AES256_GCM_SIV") shouldBe true
        }

        @Test
        fun `false when device has module data`() {
            val old = Instant.now().minusSeconds(300)
            item(metaInfo = metaInfo(), serverAddedAt = old)
                .isEncryptionIncompatible("AES256_GCM_SIV") shouldBe false
        }

        @Test
        fun `false when account uses legacy SIV`() {
            val old = Instant.now().minusSeconds(300)
            item(serverAddedAt = old).isEncryptionIncompatible("AES256_SIV") shouldBe false
        }

        @Test
        fun `false when encryption type is null`() {
            val old = Instant.now().minusSeconds(300)
            item(serverAddedAt = old).isEncryptionIncompatible(null) shouldBe false
        }

        @Test
        fun `false when device was just added`() {
            val justNow = Instant.now().minusSeconds(30)
            item(serverAddedAt = justNow).isEncryptionIncompatible("AES256_GCM_SIV") shouldBe false
        }

        @Test
        fun `false when serverAddedAt is null`() {
            item(serverAddedAt = null).isEncryptionIncompatible("AES256_GCM_SIV") shouldBe false
        }

        @Test
        fun `false when device has deserialization error`() {
            val old = Instant.now().minusSeconds(300)
            item(error = RuntimeException("bad data"), serverAddedAt = old)
                .isEncryptionIncompatible("AES256_GCM_SIV") shouldBe false
        }
    }
}
