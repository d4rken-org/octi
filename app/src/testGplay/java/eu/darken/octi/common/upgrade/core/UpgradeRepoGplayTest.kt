package eu.darken.octi.common.upgrade.core

import com.android.billingclient.api.Purchase
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.upgrade.core.billing.BillingData
import eu.darken.octi.common.upgrade.core.billing.BillingManager
import eu.darken.octi.common.upgrade.core.billing.PurchasedSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class UpgradeRepoGplayTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val billingManager = mockk<BillingManager>()
    private val billingCache = mockk<BillingCache>()
    private lateinit var lastProAtValue: DataStoreValue<Long>
    private lateinit var lastProSkuValue: DataStoreValue<String>

    // Builds a repo whose stored last-pro timestamp is `lastProAt` and last-owned sku is `lastSku`.
    // billingData is stubbed only because the upgradeInfo flow references it at construction; it is
    // never collected here.
    private fun repo(lastProAt: Long, lastSku: String = ""): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(BillingData(emptySet()))
        lastProAtValue = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns flowOf(lastProAt)
        }
        every { billingCache.lastProStateAt } returns lastProAtValue
        lastProSkuValue = mockk<DataStoreValue<String>>(relaxed = true).apply {
            every { flow } returns flowOf(lastSku)
        }
        every { billingCache.lastProStateSku } returns lastProSkuValue
        return UpgradeRepoGplay(scope, TestDispatcherProvider(), billingManager, billingCache)
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private fun purchaseOf(vararg skuIds: String) = mockk<Purchase>().apply {
        every { products } returns skuIds.toList()
        every { purchaseTime } returns 1704067200000L // 2024-01-01T00:00:00Z
    }

    private fun proPurchase() = purchaseOf(*OurSku.PRO_SKUS.map { it.id }.toTypedArray())

    @Test fun `upgrade info pro status mapping`() {
        UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = null,
        ).isPro shouldBe false

        UpgradeRepoGplay.Info(
            gracePeriod = true,
            billingData = null,
        ).isPro shouldBe true

        UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = BillingData(setOf(proPurchase())),
        ).isPro shouldBe true
    }

    @Test fun `grace period spans a week`() {
        // Keeps paying users pro through transient empty/failed Play Billing responses.
        UpgradeRepoGplay.PRO_GRACE_PERIOD shouldBe 7.days
    }

    @Test fun `restore returns pro when a purchase is found`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(lastProAt = 0L).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore keeps pro within grace when the query comes back empty`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        repo(lastProAt = now() - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore is not pro when the query is empty and grace has expired`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val expired = now() - UpgradeRepoGplay.PRO_GRACE_PERIOD.inWholeMilliseconds - 1_000
        repo(lastProAt = expired).restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `restore keeps pro within grace when the query errors`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        repo(lastProAt = now() - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore rethrows the error when it happens outside grace`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        shouldThrow<RuntimeException> {
            repo(lastProAt = 0L).restorePurchaseNow()
        }
    }

    @Test fun `restore cancellation is not converted into grace pro state`() = runTest2 {
        coEvery { billingManager.refresh() } throws CancellationException("cancelled")

        shouldThrow<CancellationException> {
            repo(lastProAt = now() - 1_000).restorePurchaseNow()
        }
    }

    @Test fun `permanent IAP keeps grace well beyond the subscription window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        // 20 days ago: past the 7-day subscription window, but within the 30-day IAP window.
        val twentyDaysAgo = now() - 20.days.inWholeMilliseconds

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Iap.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `subscription grace expires after the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = now() - 20.days.inWholeMilliseconds

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Sub.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `IAP grace window is longer than the subscription window`() {
        (UpgradeRepoGplay.PRO_GRACE_PERIOD_IAP > UpgradeRepoGplay.PRO_GRACE_PERIOD) shouldBe true
        UpgradeRepoGplay.PRO_GRACE_PERIOD_IAP shouldBe 30.days
    }

    @Test fun `preferredProSku prefers the permanent IAP when both are owned`() {
        val iap = PurchasedSku(OurSku.Iap.PRO_UPGRADE, mockk<Purchase>())
        val sub = PurchasedSku(OurSku.Sub.PRO_UPGRADE, mockk<Purchase>())

        UpgradeRepoGplay.preferredProSku(listOf(sub, iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(sub))?.id shouldBe OurSku.Sub.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(emptyList()) shouldBe null
    }

    @Test fun `unknown purchases do not refresh the grace timestamp`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(purchaseOf("some.unknown.product")))

        val info = repo(lastProAt = 0L).restorePurchaseNow()

        info.isPro shouldBe false
        coVerify(exactly = 0) { lastProAtValue.update(any()) }
        coVerify(exactly = 0) { lastProSkuValue.update(any()) }
    }

    @Test fun `unknown purchases do not suppress an active grace window`() = runTest2 {
        // An unrecognized purchase must be treated like an empty response, not like a confirmed
        // "not owned" — a user within grace stays pro.
        coEvery { billingManager.refresh() } returns BillingData(setOf(purchaseOf("some.unknown.product")))

        repo(lastProAt = now() - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `a failed IAP query does not downgrade the stored IAP sku`() = runTest2 {
        // Only a subscription came back, but the IAP query failed — the stored permanent-IAP sku
        // must survive, otherwise the grace window silently shrinks from 30d to 7d.
        coEvery { billingManager.refresh() } returns BillingData(
            purchases = setOf(purchaseOf(OurSku.Sub.PRO_UPGRADE.id)),
            iapQueryOk = false,
        )

        repo(lastProAt = now() - 1_000, lastSku = OurSku.Iap.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe true

        coVerify(exactly = 1) { lastProAtValue.update(any()) }
        coVerify(exactly = 0) { lastProSkuValue.update(any()) }
    }

    @Test fun `a confirmed missing IAP lets the subscription take over the sku`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(
            purchases = setOf(purchaseOf(OurSku.Sub.PRO_UPGRADE.id)),
            iapQueryOk = true,
        )

        repo(lastProAt = now() - 1_000, lastSku = OurSku.Iap.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe true

        coVerify(exactly = 1) { lastProSkuValue.update(match { it("") == OurSku.Sub.PRO_UPGRADE.id }) }
    }
}
