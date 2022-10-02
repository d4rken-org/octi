package eu.darken.octi.modules.power

import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest


class PowerModuleTest : BaseTest() {
    @Test
    fun `module id sanity check`() {
        PowerModule.MODULE_ID shouldBe ModuleId("eu.darken.octi.module.core.power")
    }
}