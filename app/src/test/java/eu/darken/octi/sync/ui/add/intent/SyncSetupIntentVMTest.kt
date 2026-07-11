package eu.darken.octi.sync.ui.add.intent

import androidx.lifecycle.SavedStateHandle
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavEvent
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class SyncSetupIntentVMTest : BaseTest() {

    private fun makeVM() = SyncSetupIntentVM(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(),
    )

    @Test
    fun `new sync routes to picker in CREATE mode`() = runTest2 {
        val vm = makeVM()

        vm.goNew()

        vm.navEvents.first() shouldBe NavEvent.GoTo(Nav.Sync.AddPicker(Nav.Sync.AddPicker.Mode.CREATE))
    }

    @Test
    fun `add this device routes to picker in LINK mode`() = runTest2 {
        val vm = makeVM()

        vm.goLink()

        vm.navEvents.first() shouldBe NavEvent.GoTo(Nav.Sync.AddPicker(Nav.Sync.AddPicker.Mode.LINK))
    }
}
