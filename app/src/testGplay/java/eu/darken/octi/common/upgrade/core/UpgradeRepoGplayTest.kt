package eu.darken.octi.common.upgrade.core

import com.android.billingclient.api.Purchase
import eu.darken.octi.common.datastore.DataStoreValue
import eu.darken.octi.common.upgrade.core.billing.BillingData
import eu.darken.octi.common.upgrade.core.billing.BillingManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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

    // Builds a repo whose stored last-pro timestamp is `lastProAt`. billingData is stubbed only
    // because the upgradeInfo flow references it at construction; it is never collected here.
    private fun repo(lastProAt: Long): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(BillingData(emptySet()))
        val lastPro = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns flowOf(lastProAt)
        }
        every { billingCache.lastProStateAt } returns lastPro
        return UpgradeRepoGplay(scope, TestDispatcherProvider(), billingManager, billingCache)
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private fun proPurchase() = mockk<Purchase>().apply {
        every { products } returns OurSku.PRO_SKUS.map { it.id }
        every { purchaseTime } returns 1704067200000L // 2024-01-01T00:00:00Z
    }

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
}
