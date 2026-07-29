package eu.darken.octi.common.upgrade

import eu.darken.octi.common.widget.WidgetManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Instant

class UpgradeEntitlementObserverTest : BaseTest() {

    private val upgradeInfo = MutableSharedFlow<UpgradeRepo.Info>()
    private val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).also {
        every { it.upgradeInfo } returns upgradeInfo
    }

    private val battery = mockk<WidgetManager>(relaxed = true)
    private val clipboard = mockk<WidgetManager>(relaxed = true)
    private val network = mockk<WidgetManager>(relaxed = true)
    private val widgetManagers = setOf(battery, clipboard, network)

    private fun createObserver(scope: CoroutineScope) = UpgradeEntitlementObserver(
        appScope = scope,
        upgradeRepo = upgradeRepo,
        widgetManagers = widgetManagers,
    )

    private fun proInfo(isPro: Boolean) = object : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
        override val isPro: Boolean = isPro
        override val isSettled: Boolean = true
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    @Nested
    inner class `start()` {

        @Test
        fun `first emission does not trigger refresh - widgets repaint on Glance attach`() = runTest2(autoCancel = true) {
            createObserver(this).start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()

            coVerify(exactly = 0) { battery.refreshWidgets() }
            coVerify(exactly = 0) { clipboard.refreshWidgets() }
            coVerify(exactly = 0) { network.refreshWidgets() }
        }

        @Test
        fun `isPro false to true transition refreshes all widgets`() = runTest2(autoCancel = true) {
            createObserver(this).start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = true))
            advanceUntilIdle()

            coVerify(exactly = 1) { battery.refreshWidgets() }
            coVerify(exactly = 1) { clipboard.refreshWidgets() }
            coVerify(exactly = 1) { network.refreshWidgets() }
        }

        @Test
        fun `isPro true to false transition refreshes all widgets`() = runTest2(autoCancel = true) {
            createObserver(this).start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = true))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()

            coVerify(exactly = 1) { battery.refreshWidgets() }
            coVerify(exactly = 1) { clipboard.refreshWidgets() }
            coVerify(exactly = 1) { network.refreshWidgets() }
        }

        @Test
        fun `repeated identical emissions do not refresh`() = runTest2(autoCancel = true) {
            createObserver(this).start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()

            coVerify(exactly = 0) { battery.refreshWidgets() }
        }

        @Test
        fun `multiple transitions each fire one refresh`() = runTest2(autoCancel = true) {
            createObserver(this).start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = true))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = true))
            advanceUntilIdle()

            coVerify(exactly = 3) { battery.refreshWidgets() }
            coVerify(exactly = 3) { clipboard.refreshWidgets() }
            coVerify(exactly = 3) { network.refreshWidgets() }
        }

        @Test
        fun `failing widget manager does not block sibling refreshes`() = runTest2(autoCancel = true) {
            coEvery { battery.refreshWidgets() } throws RuntimeException("boom")

            createObserver(this).start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = true))
            advanceUntilIdle()

            coVerify(exactly = 1) { clipboard.refreshWidgets() }
            coVerify(exactly = 1) { network.refreshWidgets() }
        }

        @Test
        fun `resubscribes after an upstream error and still processes later transitions`() = runTest2(autoCancel = true) {
            // A plain .catch would end the observer after the first throw; retryWhen resubscribes so
            // subsequent entitlement transitions still drive widget refreshes.
            var subscriptions = 0
            every { upgradeRepo.upgradeInfo } returns flow {
                subscriptions++
                if (subscriptions == 1) throw RuntimeException("first collection boom")
                emitAll(upgradeInfo)
            }

            createObserver(this).start()
            advanceUntilIdle()

            // Second subscription is now live on the shared flow (first was dropped by drop(1)).
            upgradeInfo.emit(proInfo(isPro = false)) // hard-locked; dropped by drop(1)
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = true))  // boundary transition -> refresh
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = false)) // boundary transition -> refresh
            advanceUntilIdle()

            subscriptions shouldBe 2
            coVerify(exactly = 2) { battery.refreshWidgets() }
            coVerify(exactly = 2) { clipboard.refreshWidgets() }
            coVerify(exactly = 2) { network.refreshWidgets() }
        }

        @Test
        fun `start is idempotent - a second call does not double-subscribe`() = runTest2(autoCancel = true) {
            val observer = createObserver(this)
            observer.start()
            observer.start()
            advanceUntilIdle()

            upgradeInfo.emit(proInfo(isPro = false))
            advanceUntilIdle()
            upgradeInfo.emit(proInfo(isPro = true))
            advanceUntilIdle()

            // A single active subscription -> exactly one refresh per transition, not two.
            coVerify(exactly = 1) { battery.refreshWidgets() }
        }
    }
}
