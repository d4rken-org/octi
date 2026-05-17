package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DeviceMetadataTest : BaseTest() {

    private fun meta(platform: String?) = DeviceMetadata(
        deviceId = DeviceId("test"),
        platform = platform,
    )

    @Test
    fun `null platform is treated as Android for backward compat`() {
        meta(null).usesAndroidReleaseVersioning shouldBe true
    }

    @Test
    fun `lowercase android is Android`() {
        meta("android").usesAndroidReleaseVersioning shouldBe true
    }

    @Test
    fun `platform check is case-insensitive`() {
        meta("Android").usesAndroidReleaseVersioning shouldBe true
        meta("ANDROID").usesAndroidReleaseVersioning shouldBe true
    }

    @Test
    fun `non-Android platforms are not Android`() {
        meta("web").usesAndroidReleaseVersioning shouldBe false
        meta("desktop").usesAndroidReleaseVersioning shouldBe false
        meta("ios").usesAndroidReleaseVersioning shouldBe false
        meta("").usesAndroidReleaseVersioning shouldBe false
    }
}
