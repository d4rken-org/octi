package eu.darken.octi.modules.meta.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Dns
import eu.darken.octi.modules.meta.R
import eu.darken.octi.modules.meta.core.MetaInfo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DeviceTypeIconsTest : BaseTest() {

    @Test
    fun `SERVER maps to the Dns icon`() {
        MetaInfo.DeviceType.SERVER.materialIcon() shouldBe Icons.TwoTone.Dns
    }

    @Test
    fun `SERVER maps to the server label`() {
        MetaInfo.DeviceType.SERVER.labelRes() shouldBe R.string.module_meta_detail_device_type_server
    }
}
