package eu.darken.octi.common.upgrade.core.billing.client

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.octi.common.upgrade.core.OurSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.util.concurrent.atomic.AtomicLong

class BillingConnectionTest : BaseTest() {

    private fun purchase(time: Long) = mockk<Purchase>().apply { every { purchaseTime } returns time }

    private fun subPurchase(token: String, autoRenewing: Boolean) = mockk<Purchase>().apply {
        every { purchaseTime } returns 1_000
        every { purchaseToken } returns token
        every { purchaseState } returns Purchase.PurchaseState.PURCHASED
        every { products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
        every { isAutoRenewing } returns autoRenewing
    }

    private fun connectionWith(
        client: BillingClient,
        overlay: Collection<Purchase> = emptyList(),
        generation: AtomicLong = AtomicLong(0),
    ): Pair<BillingConnection, MutableStateFlow<Collection<Purchase>>> {
        val purchasesGlobal = MutableStateFlow(overlay)
        val fresh = MutableSharedFlow<FreshPurchases>(replay = 1, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val freshFail = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val connection = BillingConnection(
            client = client,
            purchasesGlobal = purchasesGlobal,
            freshObservations = fresh,
            freshFailuresGlobal = freshFail,
            purchaseFailureEvents = flowOf(null),
            listenerGeneration = { generation.get() },
        )
        return connection to purchasesGlobal
    }

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
            val (connection, _) = connectionWith(client)

            connection.refreshPurchases().purchases shouldBe listOf(owned)
            // The failed type's cache is seeded empty, so the combined flow must still emit —
            // otherwise purchase acknowledgement would be starved by one broken product type.
            connection.billingData.first().purchases shouldBe listOf(owned)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `querySubscriptions prunes a stale renewing overlay when the sub is gone`() = runTest2 {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val client = mockk<BillingClient>()
            // The user cancelled + the sub lapsed: a conclusive SUBS query returns nothing.
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
                PurchasesResult(
                    BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build(),
                    emptyList(),
                )
            }
            // A stale listener record still claims the subscription auto-renews.
            val stale = subPurchase(token = "sub1", autoRenewing = true)
            val (connection, overlay) = connectionWith(client, overlay = listOf(stale))

            // No renewing sub survives — the switch-to-IAP gate can unlock.
            connection.querySubscriptions() shouldBe emptyList()
            overlay.value shouldBe emptyList()
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `querySubscriptions keeps a renewing record when a purchase event races the query`() = runTest2 {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val client = mockk<BillingClient>()
            val generation = AtomicLong(0)
            val racing = subPurchase(token = "sub2", autoRenewing = true)
            val (connection, overlay) = connectionWith(client, overlay = listOf(racing), generation = generation)

            // A purchase event lands mid-query (generation bumps): the empty query result must NOT
            // prune the newer listener record — over-blocking the switch is safe, missing it isn't.
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
                generation.incrementAndGet()
                PurchasesResult(
                    BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build(),
                    emptyList(),
                )
            }

            connection.querySubscriptions().map { it.purchaseToken } shouldBe listOf("sub2")
            overlay.value shouldBe listOf(racing)
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }

    @Test fun `querySubscriptions lets a racing renewing record win over an older query row for the same token`() = runTest2 {
        mockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        try {
            val client = mockk<BillingClient>()
            val generation = AtomicLong(0)
            // The query row says the subscription no longer renews...
            val queried = subPurchase(token = "sub1", autoRenewing = false)
            // ...but a newer listener event for the SAME token says it does. It must win, or the gate
            // would see only the stale non-renewing row and let a renewing subscriber double-buy.
            val racing = subPurchase(token = "sub1", autoRenewing = true)
            coEvery { client.queryPurchasesAsync(any<QueryPurchasesParams>()) } coAnswers {
                generation.incrementAndGet() // a purchase event raced the query
                PurchasesResult(
                    BillingResult.newBuilder().setResponseCode(BillingResponseCode.OK).build(),
                    listOf(queried),
                )
            }
            val (connection, _) = connectionWith(client, overlay = listOf(racing), generation = generation)

            connection.querySubscriptions().any { it.isAutoRenewing } shouldBe true
        } finally {
            unmockkStatic("com.android.billingclient.api.BillingClientKotlinKt")
        }
    }
}
