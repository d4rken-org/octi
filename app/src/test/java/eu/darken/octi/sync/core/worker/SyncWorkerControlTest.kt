package eu.darken.octi.sync.core.worker

import androidx.work.WorkManager
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.sync.core.SyncSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

// Robolectric: androidx.work request building (Constraints / PeriodicWorkRequestBuilder) needs the
// Android runtime. Plain Application — no Hilt app, nothing to initialize.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = android.app.Application::class)
class SyncWorkerControlTest {

    private val workManager = mockk<WorkManager>(relaxed = true)

    private fun info(isPro: Boolean, isSettled: Boolean, error: Throwable? = null) = object : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.GPLAY
        override val isPro = isPro
        override val isSettled = isSettled
        override val upgradedAt: Instant? = null
        override val error = error
    }

    private fun settings(): SyncSettings = mockk<SyncSettings>(relaxed = true).apply {
        // Background sync on, charging worker requested — the entitlement is the only variable.
        every { backgroundSyncEnabled.flow } returns flowOf(true)
        every { backgroundSyncInterval.flow } returns flowOf(60)
        every { backgroundSyncOnMobile.flow } returns flowOf(true)
        every { backgroundSyncChargingEnabled.flow } returns flowOf(true)
        every { backgroundSyncChargingInterval.flow } returns flowOf(15)
    }

    private fun upgradeRepo(info: UpgradeRepo.Info): UpgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(info)
    }

    // Unconfined scope on the test scheduler: start()'s combine collects the cold settings flows
    // and the seeded upgradeInfo eagerly, so applySchedulerConfig has run by the time start()
    // returns — no advanceUntilIdle needed, and the scheduler never idles on the open MutableStateFlow.
    private fun TestScopeRun(info: UpgradeRepo.Info, block: (SyncWorkerControl) -> Unit) = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            val control = SyncWorkerControl(
                scope = scope,
                workerManager = workManager,
                syncSettings = settings(),
                upgradeRepo = upgradeRepo(info),
            )
            control.start()
            block(control)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a pro entitlement enqueues the charging worker`() = TestScopeRun(info(isPro = true, isSettled = true)) {
        verify { workManager.enqueueUniquePeriodicWork(eq(SyncWorkerControl.WORKER_NAME_CHARGING), any(), any()) }
        verify(exactly = 0) { workManager.cancelUniqueWork(SyncWorkerControl.WORKER_NAME_CHARGING) }
    }

    @Test
    fun `a settled free entitlement cancels the charging worker`() = TestScopeRun(info(isPro = false, isSettled = true)) {
        verify { workManager.cancelUniqueWork(SyncWorkerControl.WORKER_NAME_CHARGING) }
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(eq(SyncWorkerControl.WORKER_NAME_CHARGING), any(), any())
        }
    }

    @Test
    fun `an unsettled entitlement preserves the existing charging worker`() =
        // Cold-start seed: not definitive yet. Treating it as free would cancel a paying user's
        // charging sync until billing settles, so the charging worker is left untouched.
        TestScopeRun(info(isPro = false, isSettled = false)) {
            verify(exactly = 0) {
                workManager.enqueueUniquePeriodicWork(eq(SyncWorkerControl.WORKER_NAME_CHARGING), any(), any())
            }
            verify(exactly = 0) { workManager.cancelUniqueWork(SyncWorkerControl.WORKER_NAME_CHARGING) }
        }

    @Test
    fun `a settled billing error preserves the existing charging worker`() =
        // A settled error is also not a definitive free state — it must not cancel a paying user's
        // charging worker either.
        TestScopeRun(info(isPro = false, isSettled = true, error = IllegalStateException("billing broke"))) {
            verify(exactly = 0) {
                workManager.enqueueUniquePeriodicWork(eq(SyncWorkerControl.WORKER_NAME_CHARGING), any(), any())
            }
            verify(exactly = 0) { workManager.cancelUniqueWork(SyncWorkerControl.WORKER_NAME_CHARGING) }
        }
}
