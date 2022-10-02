package eu.darken.octi.modules.meta

import eu.darken.octi.module.core.ModuleId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest


class MetaModuleTest : BaseTest() {
    @Test
    fun `module id sanity check`() {
        MetaModule.MODULE_ID shouldBe ModuleId("eu.darken.octi.module.core.meta")
    }
}