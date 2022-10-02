package eu.darken.octi.modules.wifi

import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest


class WifiModuleTest : BaseTest() {
    @Test
    fun `module id sanity check`() {
        WifiModule.MODULE_ID shouldBe ModuleId("eu.darken.octi.module.core.wifi")
    }
}