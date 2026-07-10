package eu.darken.octi.common.upgrade.core.billing.client

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingConnectionTest : BaseTest() {

    private fun purchase(time: Long) = mockk<Purchase>().apply { every { purchaseTime } returns time }

    @Test fun `combines both product types, newest first`() {
        val older = purchase(1_000)
        val newer = purchase(2_000)

        val data = BillingConnection.combinePurchaseResults(
            iaps = Result.success(listOf(older)),
            subs = Result.success(listOf(newer)),
        )
        data.purchases shouldBe listOf(newer, older)
        data.iapQueryOk shouldBe true
        data.subQueryOk shouldBe true
    }

    @Test fun `a single product-type failure does not mask a purchase found by the other`() {
        val owned = purchase(1_000)

        BillingConnection.combinePurchaseResults(
            iaps = Result.success(listOf(owned)),
            subs = Result.failure(RuntimeException("SUBS query failed")),
        ).apply {
            purchases shouldBe listOf(owned)
            iapQueryOk shouldBe true
            subQueryOk shouldBe false
        }

        BillingConnection.combinePurchaseResults(
            iaps = Result.failure(RuntimeException("IAP query failed")),
            subs = Result.success(listOf(owned)),
        ).apply {
            purchases shouldBe listOf(owned)
            iapQueryOk shouldBe false
            subQueryOk shouldBe true
        }
    }

    @Test fun `both product types empty returns empty`() {
        BillingConnection.combinePurchaseResults(
            iaps = Result.success(emptyList()),
            subs = Result.success(emptyList()),
        ).purchases shouldBe emptyList<Purchase>()
    }

    @Test fun `nothing found but a query failed rethrows the error`() {
        shouldThrow<RuntimeException> {
            BillingConnection.combinePurchaseResults(
                iaps = Result.success(emptyList()),
                subs = Result.failure(RuntimeException("SUBS query failed")),
            )
        }
    }

    @Test fun `a failing product type still lets billingData emit purchases from the other`() = runTest2 {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val client = mockk<BillingClient>()
            val owned = purchase(1_000).apply {
                every { purchaseState } returns Purchase.PurchaseState.PURCHASED
            }
            // First query (whichever product type runs first) fails, the second finds a purchase.
            // The behavior under test is symmetric, so call order doesn't matter.
            var calls = 0
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
                if (calls++ == 0) {
                    PurchasesResult(
                        BillingResult.newBuilder().setResponseCode(BillingResponseCode.ERROR).build(),
                        emptyList(),
                    )
                } else {
                    PurchasesResult(
                        BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build(),
                        listOf(owned),
                    )
                }
            }
            val connection = BillingConnection(client, flowOf(null))

            connection.refreshPurchases().purchases shouldBe listOf(owned)
            // The failed type's cache is seeded empty, so the combined flow must still emit —
            // otherwise purchase acknowledgement would be starved by one broken product type.
            connection.billingData.first().purchases shouldBe listOf(owned)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }
}
