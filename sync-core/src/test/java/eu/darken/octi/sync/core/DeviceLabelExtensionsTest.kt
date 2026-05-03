package eu.darken.octi.sync.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DeviceLabelExtensionsTest : BaseTest() {

    @Test
    fun `short label uses first eight characters`() {
        DeviceId("12345678-90ab-cdef").shortLabel shouldBe "12345678"
    }

    @Test
    fun `unique labels are unchanged`() {
        val alpha = DeviceId("aaaaaaaa-0000-0000-0000-000000000000")
        val beta = DeviceId("bbbbbbbb-0000-0000-0000-000000000000")

        disambiguateDeviceLabels(
            mapOf(
                alpha to "Pixel",
                beta to "Tablet",
            )
        ) shouldBe mapOf(
            alpha to "Pixel",
            beta to "Tablet",
        )
    }

    @Test
    fun `duplicate labels get stable device id suffixes`() {
        val alpha = DeviceId("aaaaaaaa-0000-0000-0000-000000000000")
        val beta = DeviceId("bbbbbbbb-0000-0000-0000-000000000000")

        disambiguateDeviceLabels(
            mapOf(
                alpha to "Pixel",
                beta to "pixel",
            )
        ) shouldBe mapOf(
            alpha to "Pixel (aaaaaaaa)",
            beta to "pixel (bbbbbbbb)",
        )
    }
}
