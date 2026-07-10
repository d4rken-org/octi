package eu.darken.octi.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import eu.darken.octi.common.widget.WidgetManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class UpgradeViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    private fun mockRepo(
        isProViaGrace: Boolean = false,
        wasEverPro: Boolean = false,
    ): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(
            UpgradeRepoGplay.Info(gracePeriod = isProViaGrace, billingData = null)
        )
        every { this@apply.wasEverPro } returns MutableStateFlow(wasEverPro)
        coEvery { querySkus(any()) } returns emptyList()
    }

    private fun buildVm(
        repo: UpgradeRepoGplay,
        widgetManagers: Set<WidgetManager> = emptySet(),
    ): UpgradeViewModel = UpgradeViewModel(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        upgradeRepo = repo,
        widgetManagers = widgetManagers,
    )

    @Test fun `restore with no purchase emits RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = null,
        )
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test fun `restore that times out emits RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(30_000) // longer than the 15s restore timeout
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test fun `restore that errors forwards the error instead of RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.restorePurchaseNow() } throws boom
        val vm = buildVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
    }

    @Test fun `previously-pro on this device flows into the banner flag`() = runTest2(context = testDispatcher) {
        val vm = buildVm(mockRepo(wasEverPro = true))

        val state = async { vm.state.first { !it.pricingLoading } }
        advanceUntilIdle()

        state.await().wasPreviouslyPro shouldBe true
    }

    @Test fun `banner flag stays off while grace still keeps the user pro`() = runTest2(context = testDispatcher) {
        // gracePeriod = true => Info.isPro is true even without a current raw purchase.
        val vm = buildVm(mockRepo(isProViaGrace = true, wasEverPro = true))

        val state = async { vm.state.first { !it.pricingLoading } }
        advanceUntilIdle()

        state.await().wasPreviouslyPro shouldBe false
    }

    @Test fun `banner and restore stay available when pricing is unavailable`() = runTest2(context = testDispatcher) {
        // A returning buyer with a flaky Play connection is exactly who needs restore — pricing
        // failures must not take the banner down with them.
        val repo = mockRepo(wasEverPro = true)
        coEvery { repo.querySkus(any()) } throws IllegalStateException("Play unavailable")
        val vm = buildVm(repo)

        val state = async { vm.state.first { !it.pricingLoading } }
        advanceUntilIdle()

        state.await().apply {
            pricingUnavailable shouldBe true
            pricing shouldBe null
            wasPreviouslyPro shouldBe true
        }
    }

    @Test fun `banner is already available while pricing is still loading`() = runTest2(context = testDispatcher) {
        // The banner must not wait for the SKU queries — a hanging Play pricing request (up to the
        // 5s timeout) must not delay the restore affordance for a returning buyer.
        val repo = mockRepo(wasEverPro = true)
        coEvery { repo.querySkus(any()) } coAnswers {
            delay(60_000) // never resolves within this test
            emptyList()
        }
        val vm = buildVm(repo)

        val state = async { vm.state.first() }
        runCurrent()

        state.await().apply {
            pricingLoading shouldBe true
            wasPreviouslyPro shouldBe true
        }
    }

    @Test fun `successful restore refreshes widgets`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(
            gracePeriod = true,
            billingData = null,
        )
        val widgetManager = mockk<WidgetManager>().apply { coJustRun { refreshWidgets() } }
        val vm = buildVm(repo, widgetManagers = setOf(widgetManager))

        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetManager.refreshWidgets() }
    }
}
