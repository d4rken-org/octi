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
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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

    // A manager whose provider fails `failuresBeforeSuccess` times before providing a healthy
    // connection. `attempts()` reports how many connection attempts the provider has seen.
    private fun TestScope.recoveringManager(
        failuresBeforeSuccess: Int = 1,
        failure: () -> Throwable = { BillingException("Google Play hiccup") },
    ): Pair<BillingManager, () -> Int> {
        val healthyConnection = mockk<BillingConnection>().apply {
            coEvery { refreshPurchases() } returns BillingData(emptySet())
            every { billingData } returns emptyFlow()
        }
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { connection } returns flow {
                if (attempts++ < failuresBeforeSuccess) throw failure()
                emit(healthyConnection)
                awaitCancellation() // a healthy connection stays open indefinitely
            }
        }
        return BillingManager(backgroundScope, provider) to { attempts }
    }

    @Test fun `an explicit action retries a failed connection immediately`() = runTest2 {
        val (manager, attempts) = recoveringManager()

        // A user action (restore/buy/pricing) right after the user fixed the Play/account state
        // must not keep failing on the stale cached error until the retry delay elapses.
        val timeBefore = currentTime
        manager.refresh().purchases shouldBe emptySet()

        attempts() shouldBe 2
        // On-demand retry, not the virtual clock skipping the retry delay.
        (currentTime - timeBefore) shouldBe 0L
    }

    @Test fun `connection self-heals over time without any user action`() = runTest2 {
        val (manager, attempts) = recoveringManager()

        // The ACK loop keeps the shared connection flow subscribed for the process lifetime; the
        // retry loop alone must recover the connection without anyone calling billing APIs.
        runCurrent()
        attempts() shouldBe 1

        advanceTimeBy(61_000) // past the default retry delay
        runCurrent()

        attempts() shouldBe 2
        manager.refresh().purchases shouldBe emptySet()
    }

    @Test fun `the on-demand retry wait is bounded, a wedged attempt fails with the known error`() = runTest2 {
        // First attempt fails, second attempt hangs forever (Play wedged): a user action must not
        // hang with it — after the bounded wait it fails with the stale, mapped error. This matters
        // for buy taps, which have no caller-side timeout.
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { connection } returns flow {
                if (attempts++ == 0) throw BillingException("Google Play hiccup")
                awaitCancellation() // attempt never resolves
            }
        }
        val manager = BillingManager(backgroundScope, provider)

        val timeBefore = currentTime
        shouldThrow<BillingException> { manager.refresh() }
        (currentTime - timeBefore) shouldBe 10_000L
    }

    @Test fun `billing-unavailable retries within minutes, not hours`() = runTest2 {
        val unavailable = BillingResult.newBuilder()
            .setResponseCode(BillingResponseCode.BILLING_UNAVAILABLE)
            .build()
        val (_, attempts) = recoveringManager(failure = { BillingClientException(unavailable) })

        runCurrent()
        attempts() shouldBe 1

        // Longer than the default delay, but a user signing into their Google account must not
        // stay locked out for an hour.
        advanceTimeBy(250_000)
        runCurrent()
        attempts() shouldBe 1

        advanceTimeBy(51_000) // past the 5min BILLING_UNAVAILABLE delay
        runCurrent()
        attempts() shouldBe 2
    }
}
