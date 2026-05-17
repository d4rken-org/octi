package eu.darken.octi.common.upgrade

import eu.darken.octi.common.widget.WidgetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
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
        override val upgradedAt: Instant? = null
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
    }
}
