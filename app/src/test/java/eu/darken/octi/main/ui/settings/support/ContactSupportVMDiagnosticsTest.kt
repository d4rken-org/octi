package eu.darken.octi.main.ui.settings.support

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.upgrade.UpgradeDiagnostics
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.main.core.CurriculumVitae
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
class ContactSupportVMDiagnosticsTest {

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

    private fun buildVm(
        curriculumVitae: CurriculumVitae,
        upgradeDiagnostics: UpgradeDiagnostics,
    ): ContactSupportVM = ContactSupportVM(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        sessionManager = mockk<DebugSessionManager>(relaxed = true).apply {
            every { state } returns emptyFlow()
        },
        upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns MutableStateFlow(info())
        },
        curriculumVitae = curriculumVitae,
        upgradeDiagnostics = upgradeDiagnostics,
        webpageTool = mockk<WebpageTool>(relaxed = true),
        context = context,
    )

    private fun sentBody(): String? {
        val chooser = Shadows.shadowOf(context as Application).nextStartedActivity ?: return null
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: chooser
        return send.getStringExtra(Intent.EXTRA_TEXT)
    }

    @Test
    fun `both diagnostics reads are included in the support body`() = runTest(testDispatcher) {
        val cv = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory }
        val diag = mockk<UpgradeDiagnostics>().apply { coEvery { debugInfo() } returns "DIAG-SENTINEL" }
        val vm = buildVm(cv, diag)

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldContain "ProHistory: "
        body shouldContain "graceEngagedCount=3"
        body shouldContain "DIAG-SENTINEL"
    }

    @Test
    fun `a failing store does not suppress the other diagnostic`() = runTest(testDispatcher) {
        // The pro-state counters and the billing diagnostics live in different stores — one failing
        // must not stop the email or the other's evidence.
        val cv = mockk<CurriculumVitae>().apply {
            coEvery { proHistory() } throws IllegalStateException("cv down")
        }
        val diag = mockk<UpgradeDiagnostics>().apply { coEvery { debugInfo() } returns "DIAG-SENTINEL" }
        val vm = buildVm(cv, diag)

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldContain "DIAG-SENTINEL"
        body shouldNotContain "graceEngagedCount"
    }

    @Test
    fun `a slow diagnostic is dropped on timeout but the email still sends`() = runTest(testDispatcher) {
        val cv = mockk<CurriculumVitae>().apply { coEvery { proHistory() } returns proHistory }
        val diag = mockk<UpgradeDiagnostics>().apply {
            coEvery { debugInfo() } coAnswers {
                delay(30_000) // far longer than the 2s diagnostics timeout
                "TOO-SLOW"
            }
        }
        val vm = buildVm(cv, diag)

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldContain "graceEngagedCount=3"
        body shouldNotContain "TOO-SLOW"
    }

    @Test
    fun `a cancelled diagnostic read aborts the send instead of emailing`() = runTest(testDispatcher) {
        // Cancellation must propagate, never be swallowed into a "successful" email.
        val cv = mockk<CurriculumVitae>().apply {
            coEvery { proHistory() } throws CancellationException("cancelled")
        }
        val diag = mockk<UpgradeDiagnostics>().apply { coEvery { debugInfo() } returns "DIAG-SENTINEL" }
        val vm = buildVm(cv, diag)

        vm.sendEmail()
        advanceUntilIdle()

        sentBody().shouldBeNull()
    }
}
