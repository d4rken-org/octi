package eu.darken.octi.modules.connectivity

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ConnectivityModuleTest : BaseTest() {

    @Test
    fun `module ID is correct`() {
        ConnectivityModule.MODULE_ID.id shouldBe "eu.darken.octi.module.core.connectivity"
    }
}
