package eu.darken.octi.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.android.billingclient.api.Purchase
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import eu.darken.octi.common.upgrade.core.billing.BillingData
import eu.darken.octi.common.widget.WidgetManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Instant

class UpgradeViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()
    private val webpageTool = mockk<WebpageTool>(relaxed = true)
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(BASE_MS)
    }
    private val activity = mockk<Activity>(relaxed = true)

    private fun infoOf(vararg purchases: Purchase) = UpgradeRepoGplay.Info(
        gracePeriod = false,
        billingData = BillingData(purchases.toSet()),
    )

    private fun mockRepo(
        info: UpgradeRepoGplay.Info = UpgradeRepoGplay.Info(gracePeriod = false, billingData = null),
        wasEverPro: Boolean = false,
        settled: Boolean = true,
    ): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(info)
        every { this@apply.wasEverPro } returns MutableStateFlow(wasEverPro)
        every { proUnconfirmedSince } returns MutableStateFlow(0L)
        every { isSettled } returns MutableStateFlow(settled)
        every { autoRestoreInProgress } returns MutableStateFlow(false)
        coEvery { querySkus(any()) } returns emptyList()
    }

    private fun buildVm(
        repo: UpgradeRepoGplay,
        widgetManagers: Set<WidgetManager> = emptySet(),
        forced: Boolean = false,
        manage: Boolean = false,
        autoInit: Boolean = true,
    ): UpgradeViewModel = UpgradeViewModel(
        handle = SavedStateHandle(),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        upgradeRepo = repo,
        widgetManagers = widgetManagers,
        webpageTool = webpageTool,
        clock = clock,
    ).apply { if (autoInit) initialize(forced = forced, manage = manage) }

    private fun purchase(skuId: String, autoRenewing: Boolean) = mockk<Purchase>().apply {
        every { products } returns listOf(skuId)
        every { purchaseTime } returns 1704067200000L
        every { isAutoRenewing } returns autoRenewing
    }

    @Test fun `iap gate blocks while the subscription still auto-renews`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns listOf(purchase(OurSku.Sub.PRO_UPGRADE.id, autoRenewing = true))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(activity)
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionStillRenewing
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any()) }
    }

    @Test fun `iap gate reports check-failed on verification timeout`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(20_000) // longer than VERIFY_TIMEOUT_MS
            emptyList()
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(activity)
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionCheckFailed
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any()) }
    }

    @Test fun `iap gate forwards a verification error`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.queryCurrentSubscriptions() } throws boom
        val vm = buildVm(repo)

        val error = async { vm.errorEvents.first() }
        vm.onGoIap(activity)
        advanceUntilIdle()

        error.await() shouldBe boom
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any()) }
    }

    @Test fun `iap gate launches when no subscription is renewing`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns emptyList()
        coJustRun { repo.launchBillingFlowNow(any(), any(), any()) }
        val vm = buildVm(repo)

        vm.onGoIap(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(activity, OurSku.Iap.PRO_UPGRADE, null) }
    }

    @Test fun `restore with only grace shows failed and does not refresh widgets`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        // Grace-only isPro: Play still couldn't confirm a real purchase.
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        val widget = mockk<WidgetManager>().apply { coJustRun { refreshWidgets() } }
        val vm = buildVm(repo, widgetManagers = setOf(widget))

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
        coVerify(exactly = 0) { widget.refreshWidgets() }
    }

    @Test fun `restore with a real purchase succeeds and refreshes widgets`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns infoOf(purchase(OurSku.Iap.PRO_UPGRADE.id, autoRenewing = false))
        val widget = mockk<WidgetManager>().apply { coJustRun { refreshWidgets() } }
        val vm = buildVm(repo, widgetManagers = setOf(widget))

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreSucceeded
        coVerify(exactly = 1) { widget.refreshWidgets() }
    }

    @Test fun `restore that errors forwards the error`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.restorePurchaseNow() } throws boom
        val vm = buildVm(repo)

        val error = async { vm.errorEvents.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        error.await() shouldBe boom
    }

    @Test fun `an in-flight restore blocks the iap gate`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        vm.restorePurchase()
        runCurrent() // restore admitted, holding the guard
        vm.onGoIap(activity)
        advanceUntilIdle()

        // The gate never even queried subscriptions — the single guard rejected it.
        coVerify(exactly = 0) { repo.queryCurrentSubscriptions() }
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any()) }
    }

    @Test fun `an immediate already-owned launch reconciles by restoring`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns emptyList()
        coEvery { repo.launchBillingFlowNow(any(), OurSku.Iap.PRO_UPGRADE, null) } throws
            eu.darken.octi.common.upgrade.core.billing.ItemAlreadyOwnedBillingException()
        coEvery { repo.restorePurchaseNow() } returns infoOf(purchase(OurSku.Iap.PRO_UPGRADE.id, autoRenewing = false))
        val widget = mockk<WidgetManager>().apply { coJustRun { refreshWidgets() } }
        val vm = buildVm(repo, widgetManagers = setOf(widget))

        val event = async { vm.events.first() }
        vm.onGoIap(activity)
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreSucceeded
        coVerify(exactly = 1) { widget.refreshWidgets() }
    }

    @Test fun `restore is single-flight`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        vm.restorePurchase()
        vm.restorePurchase()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.restorePurchaseNow() }
    }

    @Test fun `owner sees the switch offer, subscription button disabled`() = runTest2(context = testDispatcher) {
        val repo = mockRepo(info = infoOf(purchase(OurSku.Sub.PRO_UPGRADE.id, autoRenewing = false)))
        val vm = buildVm(repo)

        val loaded = async { vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded }
        advanceUntilIdle()

        loaded.await().apply {
            ownership.subscription?.isAutoRenewing shouldBe false
            subscriptionEnabled shouldBe false
        }
    }

    @Test fun `purchase actions stay disabled until settled`() = runTest2(context = testDispatcher) {
        val repo = mockRepo(settled = false)
        val vm = buildVm(repo)

        // runCurrent only: advancing to idle would trip the bounded settle fallback timer.
        val loaded = async { vm.state.first { it is UpgradeUiState.Loaded } as UpgradeUiState.Loaded }
        runCurrent()

        loaded.await().apply {
            settled shouldBe false
            iapEnabled shouldBe false
            subscriptionEnabled shouldBe false
        }
    }

    @Test fun `sales route auto-closes once the user is pro`() = runTest2(context = testDispatcher) {
        val repo = mockRepo(info = infoOf(purchase(OurSku.Iap.PRO_UPGRADE.id, autoRenewing = false)))
        val vm = buildVm(repo, forced = false, manage = false, autoInit = false)

        // Subscribe before initializing so the navUp event can't be emitted before we collect.
        val navs = mutableListOf<Any>()
        val job = launch { vm.navEvents.collect { navs.add(it) } }
        runCurrent()
        vm.initialize(forced = false, manage = false)
        advanceUntilIdle()

        navs.isNotEmpty() shouldBe true
        job.cancel()
    }

    @Test fun `manage route does not auto-close when pro`() = runTest2(context = testDispatcher) {
        val repo = mockRepo(info = infoOf(purchase(OurSku.Iap.PRO_UPGRADE.id, autoRenewing = false)))
        val vm = buildVm(repo, forced = false, manage = true, autoInit = false)

        val navs = mutableListOf<Any>()
        val job = launch { vm.navEvents.collect { navs.add(it) } }
        runCurrent()
        vm.initialize(forced = false, manage = true)
        advanceUntilIdle()

        navs.isEmpty() shouldBe true
        job.cancel()
    }

    companion object {
        private const val BASE_MS = 40L * 365 * 24 * 60 * 60 * 1000
    }
}
