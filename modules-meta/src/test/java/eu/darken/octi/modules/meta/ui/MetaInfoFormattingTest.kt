package eu.darken.octi.modules.meta.ui

import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class MetaInfoFormattingTest : BaseTest() {

    private fun metaInfo(
        osType: String? = null,
        osVersionName: String? = null,
        androidVersionName: String? = null,
        androidApiLevel: Int? = null,
    ) = MetaInfo(
        deviceLabel = null,
        deviceId = DeviceId(id = "device-123"),
        octiVersionName = "1.2.3",
        octiGitSha = "abcdef",
        deviceManufacturer = "TestCorp",
        deviceName = "TestDevice",
        deviceType = MetaInfo.DeviceType.PHONE,
        androidVersionName = androidVersionName,
        androidApiLevel = androidApiLevel,
        osType = osType,
        osVersionName = osVersionName,
    )

    @Nested
    inner class `android peers` {
        @Test
        fun `generic and legacy fields both populated`() {
            metaInfo(
                osType = "android",
                osVersionName = "14",
                androidVersionName = "14",
                androidApiLevel = 34,
            ).osDisplayName() shouldBe "Android 14 (API 34)"
        }

        @Test
        fun `legacy only payload still renders the api level`() {
            metaInfo(
                androidVersionName = "14",
                androidApiLevel = 34,
            ).osDisplayName() shouldBe "Android 14 (API 34)"
        }

        @Test
        fun `osType android with legacy version renders the api level`() {
            metaInfo(
                osType = "android",
                androidVersionName = "14",
                androidApiLevel = 34,
            ).osDisplayName() shouldBe "Android 14 (API 34)"
        }

        @Test
        fun `no version at all renders the bare family`() {
            metaInfo(
                osType = "android",
                androidApiLevel = 34,
            ).osDisplayName() shouldBe "Android"
        }
    }

    @Nested
    inner class `windows peers` {
        @Test
        fun `plain family with marketing version`() {
            metaInfo(osType = "windows", osVersionName = "11").osDisplayName() shouldBe "Windows 11"
        }

        @Test
        fun `inline version wins over the kernel version`() {
            metaInfo(osType = "Windows 11", osVersionName = "10.0").osDisplayName() shouldBe "Windows 11"
        }

        @Test
        fun `inline version survives sloppy whitespace`() {
            metaInfo(osType = "Windows  11", osVersionName = "10.0").osDisplayName() shouldBe "Windows 11"
        }

        @Test
        fun `kernel version is neither appended nor substituted`() {
            val rendered = metaInfo(osType = "Windows 11", osVersionName = "10.0").osDisplayName()
            rendered shouldNotBe "Windows 11 10.0"
            rendered shouldNotBe "Windows 10.0"
        }
    }

    @Nested
    inner class `desktop peers` {
        @Test
        fun `mac os x spelling maps to macOS`() {
            metaInfo(osType = "Mac OS X", osVersionName = "15.3").osDisplayName() shouldBe "macOS 15.3"
        }

        @Test
        fun `mac os spelling maps to macOS`() {
            metaInfo(osType = "mac os", osVersionName = "15.3").osDisplayName() shouldBe "macOS 15.3"
        }

        @Test
        fun `linux keeps its full kernel version`() {
            metaInfo(
                osType = "Linux",
                osVersionName = "6.8.0-137-generic",
            ).osDisplayName() shouldBe "Linux 6.8.0-137-generic"
        }
    }

    @Nested
    inner class `unknown os types` {
        @Test
        fun `descriptor is not treated as a version and does not suppress osVersionName`() {
            metaInfo(
                osType = "Chrome OS Flex",
                osVersionName = "15699.66.0",
            ).osDisplayName() shouldBe "Chrome OS Flex 15699.66.0"
        }

        @Test
        fun `distro name keeps its own version`() {
            metaInfo(osType = "Linux Mint", osVersionName = "6.8.0").osDisplayName() shouldBe "Linux Mint 6.8.0"
        }

        @Test
        fun `snake case identifier is humanized in full`() {
            metaInfo(osType = "home_assistant").osDisplayName() shouldBe "Home Assistant"
        }

        @Test
        fun `single token is capitalized`() {
            metaInfo(osType = "other").osDisplayName() shouldBe "Other"
        }

        @Test
        fun `chromeosx is not matched as chromeos`() {
            metaInfo(osType = "chromeosx").osDisplayName() shouldBe "Chromeosx"
        }

        @Test
        fun `windowsphone is not matched as windows`() {
            metaInfo(osType = "windowsphone").osDisplayName() shouldBe "Windowsphone"
        }
    }

    @Nested
    inner class `degenerate payloads` {
        @Test
        fun `legacy android version does not leak into a non android label`() {
            metaInfo(
                osType = "linux",
                androidVersionName = "14",
                androidApiLevel = 34,
            ).osDisplayName() shouldBe "Linux"
        }

        @Test
        fun `missing osType renders the bare version`() {
            metaInfo(osVersionName = "15.3").osDisplayName() shouldBe "15.3"
        }

        @Test
        fun `blank osType renders the bare version`() {
            metaInfo(osType = "   ", osVersionName = "15.3").osDisplayName() shouldBe "15.3"
        }

        @Test
        fun `no os metadata at all returns null`() {
            metaInfo().osDisplayName() shouldBe null
        }
    }
}
