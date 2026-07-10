package eu.darken.octi.common.upgrade.core.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import eu.darken.octi.common.upgrade.core.billing.client.BillingClientException
import eu.darken.octi.common.upgrade.core.billing.client.BillingConnection
import eu.darken.octi.common.upgrade.core.billing.client.BillingConnectionProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingManagerTest : BaseTest() {

    @Test fun `connection failure surfaces to refresh callers instead of hanging`() = runTest2 {
        val provider = mockk<BillingConnectionProvider>().apply {
            every { connection } returns flow { throw BillingException("Google Play unavailable") }
        }
        val manager = BillingManager(backgroundScope, provider)

        shouldThrow<BillingException> {
            manager.refresh()
        }
    }

    @Test fun `connection setup failure maps to the user-friendly Play error`() = runTest2 {
        val unavailable = BillingResult.newBuilder()
            .setResponseCode(BillingResponseCode.BILLING_UNAVAILABLE)
            .build()
        val provider = mockk<BillingConnectionProvider>().apply {
            every { connection } returns flow { throw BillingClientException(unavailable) }
        }
        val manager = BillingManager(backgroundScope, provider)

        shouldThrow<GplayServiceUnavailableException> {
            manager.refresh()
        }
    }

    @Test fun `connection recovers after an earlier failure without a process restart`() = runTest2 {
        val healthyConnection = mockk<BillingConnection>().apply {
            coEvery { refreshPurchases() } returns BillingData(emptySet())
            every { billingData } returns emptyFlow()
        }
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { connection } returns flow {
                if (attempts++ == 0) throw BillingException("Google Play hiccup")
                emit(healthyConnection)
                awaitCancellation() // a healthy connection stays open indefinitely
            }
        }
        val manager = BillingManager(backgroundScope, provider)

        // The ACK loop keeps the shared connection flow subscribed for the process lifetime, so a
        // failure Result must not stick in the replay cache forever.
        shouldThrow<BillingException> { manager.refresh() }

        advanceTimeBy(61_000) // past the connection retry delay

        manager.refresh().purchases shouldBe emptySet()
        attempts shouldBe 2
    }
}
