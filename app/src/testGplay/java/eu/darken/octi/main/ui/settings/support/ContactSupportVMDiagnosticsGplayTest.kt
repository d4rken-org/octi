package eu.darken.octi.main.ui.settings.support

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.core.BillingCache
import eu.darken.octi.common.upgrade.core.HangingPreferencesDataStore
import eu.darken.octi.common.upgrade.core.UpgradeDiagnosticsGplay
import eu.darken.octi.main.core.CurriculumVitae
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import testhelpers.coroutine.TestDispatcherProvider
import kotlin.time.Instant

// Robolectric: sendEmail() builds an Intent and calls context.startActivity, which we capture via
// the shadow application to assert what reached the support email body.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ContactSupportVMDiagnosticsGplayTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val proHistory = CurriculumVitae.ProHistory(
        lastState = CurriculumVitae.ProState.PURCHASED,
        graceEngagedCount = 3,
        graceEngagedLast = null,
        proLostCount = 0,
        proLostLast = null,
    )

    private fun info(isPro: Boolean = true) = object : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.GPLAY
        override val isPro = isPro
        override val isSettled = true
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    private fun sentBody(): String? {
        val chooser = Shadows.shadowOf(context as Application).nextStartedActivity ?: return null
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: chooser
        return send.getStringExtra(Intent.EXTRA_TEXT)
    }

    @Test
    fun `a wedged billing cache reaches the support email as unavailable`() = runTest(testDispatcher) {
        // The diagnostics bound its own billing cache read; the VM's outer budget must be wide
        // enough to let that verdict come back. If the outer budget expires first, the email
        // silently ships without the diagnostic instead of with "BillingCache=unavailable".
        val vm = ContactSupportVM(
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            sessionManager = mockk<DebugSessionManager>(relaxed = true).apply {
                every { state } returns emptyFlow()
            },
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
                every { upgradeInfo } returns MutableStateFlow(info())
            },
            curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
            upgradeDiagnostics = UpgradeDiagnosticsGplay(
                billingCache = BillingCache(HangingPreferencesDataStore()).apply { cacheTimeoutMs = 50L },
                curriculumVitae = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory },
            ),
            webpageTool = mockk<WebpageTool>(relaxed = true),
            context = context,
        )

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldContain "BillingCache=unavailable"
        body shouldNotContain "lastProStateAt=never"
    }
}
