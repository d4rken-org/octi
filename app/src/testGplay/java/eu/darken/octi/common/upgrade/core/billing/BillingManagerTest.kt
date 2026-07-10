package eu.darken.octi.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.billing.client.BillingClientException
import eu.darken.octi.common.upgrade.core.billing.client.BillingConnection
import eu.darken.octi.common.upgrade.core.billing.client.BillingConnectionProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingManagerTest : BaseTest() {

    private val activity = mockk<Activity>()

    @BeforeEach
    fun setup() {
        mockkObject(Bugs)
        justRun { Bugs.report(any()) }
    }

    @AfterEach
    fun teardown() {
        unmockkObject(Bugs)
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    // A manager whose connection fails launchBillingFlow with the given launch-result code —
    // the path Play uses for immediate "buy" failures (returned result, not an exception).
    private fun TestScope.manager(launchFailureCode: Int): BillingManager {
        val connection = mockk<BillingConnection>().apply {
            coEvery { refreshPurchases() } returns BillingData(emptySet())
            every { billingData } returns emptyFlow()
            coEvery { launchBillingFlow(any(), any(), null) } throws BillingClientException(result(launchFailureCode))
        }
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flowOf(connection)
        }
        return BillingManager(backgroundScope, provider)
    }

    @Test fun `failed launch surfaces as the already-owned user error and is reported`() = runTest2 {
        val manager = manager(BillingResponseCode.ITEM_ALREADY_OWNED)

        shouldThrow<ItemAlreadyOwnedBillingException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 1) { Bugs.report(any()) }
    }

    @Test fun `user cancel from the launch result stays silent bug-report-wise`() = runTest2 {
        val manager = manager(BillingResponseCode.USER_CANCELED)

        shouldThrow<UserCanceledBillingException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `billing-unavailable maps to the service error without a bug report`() = runTest2 {
        val manager = manager(BillingResponseCode.BILLING_UNAVAILABLE)

        shouldThrow<GplayServiceUnavailableException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `developer errors are rethrown and reported`() = runTest2 {
        val manager = manager(BillingResponseCode.DEVELOPER_ERROR)

        shouldThrow<BillingClientException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 1) { Bugs.report(any()) }
    }

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
