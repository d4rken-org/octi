package eu.darken.octi.main.ui.onboarding.connect

import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavEvent
import eu.darken.octi.main.core.GeneralSettings
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class ConnectVMTest : BaseTest() {

    private val onboardingUpdate = slot<(Boolean) -> Boolean?>()
    private val onboardingDone = mockk<DataStoreValue<Boolean>>().apply {
        coEvery { update(capture(onboardingUpdate)) } returns mockk()
    }
    private val generalSettings = mockk<GeneralSettings>().apply {
        every { isOnboardingDone } returns onboardingDone
    }

    private fun makeVM() = ConnectVM(
        dispatcherProvider = TestDispatcherProvider(),
        generalSettings = generalSettings,
    )

    @Test
    fun `set up sync finishes onboarding and stacks dashboard under sync list`() = runTest2 {
        val vm = makeVM()

        vm.finishWithSyncSetup()

        vm.navEvents.take(2).toList() shouldContainExactly listOf(
            NavEvent.GoTo(Nav.Main.Dashboard, popUpTo = Nav.Main.Welcome, inclusive = true),
            NavEvent.GoTo(Nav.Sync.List),
        )
        coVerify(exactly = 1) { onboardingDone.update(any()) }
        onboardingUpdate.captured(false) shouldBe true
    }

    @Test
    fun `maybe later finishes onboarding and goes to dashboard only`() = runTest2 {
        val vm = makeVM()

        vm.finishToDashboard()

        vm.navEvents.first() shouldBe NavEvent.GoTo(
            Nav.Main.Dashboard,
            popUpTo = Nav.Main.Welcome,
            inclusive = true,
        )
        withTimeoutOrNull(100) { vm.navEvents.firstOrNull() } shouldBe null
        coVerify(exactly = 1) { onboardingDone.update(any()) }
    }

    @Test
    fun `double tap only finishes once`() = runTest2 {
        val vm = makeVM()

        vm.finishWithSyncSetup()
        vm.finishWithSyncSetup()
        vm.finishToDashboard()

        vm.navEvents.take(2).toList() shouldContainExactly listOf(
            NavEvent.GoTo(Nav.Main.Dashboard, popUpTo = Nav.Main.Welcome, inclusive = true),
            NavEvent.GoTo(Nav.Sync.List),
        )
        withTimeoutOrNull(100) { vm.navEvents.firstOrNull() } shouldBe null
        coVerify(exactly = 1) { onboardingDone.update(any()) }
    }
}
