package eu.darken.octi.common.upgrade.core

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.upgrade.core.billing.BillingData
import eu.darken.octi.common.upgrade.core.billing.BillingManager
import eu.darken.octi.common.upgrade.core.billing.FreshBillingData
import eu.darken.octi.common.upgrade.core.billing.PurchasedSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class UpgradeRepoGplayTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val billingManager = mockk<BillingManager>()
    private val billingCache = mockk<BillingCache>()
    private val clock = FakeClock(BASE_MS)

    private class FakeClock(var nowMs: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    private fun repo(
        lastProAt: Long = 0L,
        lastSku: String = "",
        billingData: BillingData = BillingData(emptySet()),
        fresh: FreshBillingData? = null,
        refreshFailures: Int = 0,
        purchaseFailures: List<BillingResult> = emptyList(),
    ): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(billingData)
        every { billingManager.freshBillingData } returns (fresh?.let { flowOf(it) } ?: emptyFlow())
        every { billingManager.refreshFailures } returns
            if (refreshFailures == 0) emptyFlow() else flowOf(*Array(refreshFailures) { Unit })
        every { billingManager.purchaseFailures } returns
            if (purchaseFailures.isEmpty()) emptyFlow() else flowOf(*purchaseFailures.toTypedArray())

        every { billingCache.lastProStateAt } returns dataStoreValue(lastProAt)
        every { billingCache.lastProStateSku } returns dataStoreValue(lastSku)
        every { billingCache.proUnconfirmedAt } returns dataStoreValue(0L)
        coEvery { billingCache.stampLastProState(any(), any()) } just Runs
        coEvery { billingCache.recordProUnconfirmed(any()) } just Runs

        return UpgradeRepoGplay(scope, TestDispatcherProvider(), billingManager, billingCache, clock)
    }

    private fun <T> dataStoreValue(value: T): DataStoreValue<T> = mockk<DataStoreValue<T>>(relaxed = true).apply {
        every { flow } returns flowOf(value)
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    private fun purchaseOf(vararg skuIds: String) = mockk<Purchase>().apply {
        every { products } returns skuIds.toList()
        every { purchaseTime } returns 1704067200000L // 2024-01-01T00:00:00Z
    }

    private fun proPurchase() = purchaseOf(*OurSku.PRO_SKUS.map { it.id }.toTypedArray())

    private fun freshOf(data: BillingData, full: Boolean = true) = FreshBillingData(data, full)

    @Test fun `upgrade info pro status mapping`() {
        UpgradeRepoGplay.Info(gracePeriod = false, billingData = null).isPro shouldBe false
        UpgradeRepoGplay.Info(gracePeriod = true, billingData = null).isPro shouldBe true
        UpgradeRepoGplay.Info(gracePeriod = false, billingData = BillingData(setOf(proPurchase()))).isPro shouldBe true
    }

    @Test fun `grace windows`() {
        UpgradeRepoGplay.PRO_GRACE_PERIOD shouldBe 7.days
        UpgradeRepoGplay.PRO_GRACE_PERIOD_IAP shouldBe 30.days
        (UpgradeRepoGplay.PRO_GRACE_PERIOD_IAP > UpgradeRepoGplay.PRO_GRACE_PERIOD) shouldBe true
    }

    @Test fun `restore returns pro when a purchase is found`() = runTest2 {
        coEvery { billingManager.refresh() } returns freshOf(BillingData(setOf(proPurchase())))
        repo(lastProAt = 0L).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore keeps pro within grace when the query comes back empty`() = runTest2 {
        coEvery { billingManager.refresh() } returns freshOf(BillingData(emptySet()))
        repo(lastProAt = BASE_MS - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore is not pro when empty and grace expired`() = runTest2 {
        coEvery { billingManager.refresh() } returns freshOf(BillingData(emptySet()))
        val expired = BASE_MS - UpgradeRepoGplay.PRO_GRACE_PERIOD.inWholeMilliseconds - 1_000
        repo(lastProAt = expired).restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `restore keeps pro within grace when the query errors`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")
        repo(lastProAt = BASE_MS - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore rethrows the error when it happens outside grace`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")
        shouldThrow<RuntimeException> { repo(lastProAt = 0L).restorePurchaseNow() }
    }

    @Test fun `restore cancellation is not converted into grace pro state`() = runTest2 {
        coEvery { billingManager.refresh() } throws CancellationException("cancelled")
        shouldThrow<CancellationException> { repo(lastProAt = BASE_MS - 1_000).restorePurchaseNow() }
    }

    @Test fun `permanent IAP keeps grace beyond the subscription window`() = runTest2 {
        coEvery { billingManager.refresh() } returns freshOf(BillingData(emptySet()))
        repo(lastProAt = BASE_MS - 20.days.inWholeMilliseconds, lastSku = OurSku.Iap.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `subscription grace expires after the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns freshOf(BillingData(emptySet()))
        repo(lastProAt = BASE_MS - 20.days.inWholeMilliseconds, lastSku = OurSku.Sub.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `preferredProSku prefers the permanent IAP when both are owned`() {
        val iap = PurchasedSku(OurSku.Iap.PRO_UPGRADE, mockk<Purchase>())
        val sub = PurchasedSku(OurSku.Sub.PRO_UPGRADE, mockk<Purchase>())
        UpgradeRepoGplay.preferredProSku(listOf(sub, iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(sub))?.id shouldBe OurSku.Sub.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(emptyList()) shouldBe null
    }

    @Test fun `a fresh full snapshot with a pro purchase stamps the anchor`() = runTest2 {
        repo(lastProAt = 0L, fresh = freshOf(BillingData(setOf(proPurchase())), full = true))
        coVerify(exactly = 1) { billingCache.stampLastProState(any(), any()) }
        coVerify(exactly = 0) { billingCache.recordProUnconfirmed(any()) }
    }

    @Test fun `a fresh full snapshot without a pro purchase opens the episode`() = runTest2 {
        repo(lastProAt = BASE_MS - 120_000, fresh = freshOf(BillingData(emptySet()), full = true))
        coVerify(exactly = 1) { billingCache.recordProUnconfirmed(any()) }
        coVerify(exactly = 0) { billingCache.stampLastProState(any(), any()) }
    }

    @Test fun `presence-only fresh data without a pro purchase records nothing`() = runTest2 {
        repo(lastProAt = BASE_MS - 120_000, fresh = freshOf(BillingData(emptySet()), full = false))
        coVerify(exactly = 0) { billingCache.recordProUnconfirmed(any()) }
        coVerify(exactly = 0) { billingCache.stampLastProState(any(), any()) }
    }

    @Test fun `a failed fresh attempt opens the episode`() = runTest2 {
        repo(lastProAt = BASE_MS - 120_000, refreshFailures = 1)
        coVerify(exactly = 1) { billingCache.recordProUnconfirmed(any()) }
    }

    @Test fun `a background refresh failure opens the unconfirmed episode`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")
        repo(lastProAt = BASE_MS - 1_000).refresh()
        coVerify(atLeast = 1) { billingCache.recordProUnconfirmed(any()) }
    }

    @Test fun `a restore error within grace opens the unconfirmed episode`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")
        repo(lastProAt = BASE_MS - 1_000).restorePurchaseNow().isPro shouldBe true
        coVerify(atLeast = 1) { billingCache.recordProUnconfirmed(any()) }
    }

    @Test fun `an IAP anchor is kept sticky when only a subscription is seen`() = runTest2 {
        repo(lastSku = OurSku.Iap.PRO_UPGRADE.id, fresh = freshOf(BillingData(setOf(purchaseOf(OurSku.Sub.PRO_UPGRADE.id)))))
        coVerify(exactly = 1) { billingCache.stampLastProState(null, any()) }
    }

    @Test fun `a subscription takes over the sku when there is no IAP anchor`() = runTest2 {
        repo(lastSku = "", fresh = freshOf(BillingData(setOf(purchaseOf(OurSku.Sub.PRO_UPGRADE.id)))))
        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Sub.PRO_UPGRADE.id, any()) }
    }

    @Test fun `async already-owned purchase event triggers a silent restore`() = runTest2 {
        coEvery { billingManager.refresh() } returns freshOf(BillingData(setOf(proPurchase())))
        repo(lastProAt = 0L, purchaseFailures = listOf(result(BillingResponseCode.ITEM_ALREADY_OWNED)))
        coVerify(exactly = 1) { billingManager.refresh() }
    }

    @Test fun `other async purchase failures do not trigger a restore`() = runTest2 {
        repo(lastProAt = 0L, purchaseFailures = listOf(result(BillingResponseCode.DEVELOPER_ERROR)))
        coVerify(exactly = 0) { billingManager.refresh() }
    }

    companion object {
        // Fixed "now" well past the grace windows so relative timestamps stay positive.
        private const val BASE_MS = 40L * 365 * 24 * 60 * 60 * 1000
    }
}
