package eu.darken.octi.main.ui.settings.support

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.debug.recording.core.DebugSessionManager
import eu.darken.octi.common.upgrade.UpgradeRepo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
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
class ContactSupportVMBodyTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun info() = object : UpgradeRepo.Info {
        override val type = UpgradeRepo.Type.GPLAY
        override val isPro = true
        override val isSettled = true
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    private fun buildVm(
        upgradeInfoFlow: Flow<UpgradeRepo.Info> = MutableStateFlow(info()),
    ): ContactSupportVM = ContactSupportVM(
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        sessionManager = mockk<DebugSessionManager>(relaxed = true).apply {
            every { state } returns emptyFlow()
        },
        upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns upgradeInfoFlow
        },
        webpageTool = mockk<WebpageTool>(relaxed = true),
        context = context,
    )

    private fun sentBody(): String? {
        val chooser = Shadows.shadowOf(context as Application).nextStartedActivity ?: return null
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: chooser
        return send.getStringExtra(Intent.EXTRA_TEXT)
    }

    @Test
    fun `the support body carries the device info block`() = runTest(testDispatcher) {
        val vm = buildVm()

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldContain "--- Device Info ---"
        body shouldContain "App: "
        body shouldContain "Android: "
        body shouldContain "Device: "
    }

    @Test
    fun `the support body carries no billing diagnostics`() = runTest(testDispatcher) {
        // Regression guard: the flavor billing diagnostics belong in the debug log header, where
        // exactly one consumer renders them, not in the mail.
        val vm = buildVm()

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldNotContain "--- Billing Diagnostics ---"
        body shouldNotContain "ProHistory"
        body shouldNotContain "BillingCache"
    }

    @Test
    fun `a bug report carries the expected behavior block`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.setCategory(ContactSupportVM.Category.BUG_REPORT)
        vm.setExpectedBehavior("The sync should have finished")
        advanceUntilIdle()

        vm.sendEmail()
        advanceUntilIdle()

        val body = sentBody()!!
        body shouldContain "--- Expected Behavior ---"
        body shouldContain "The sync should have finished"
    }

    @Test
    fun `a cancelled entitlement read aborts the send instead of emailing`() = runTest(testDispatcher) {
        // Cancellation must propagate, never be swallowed into a "successful" email.
        val vm = buildVm(upgradeInfoFlow = flow { throw CancellationException("cancelled") })

        vm.sendEmail()
        advanceUntilIdle()

        sentBody().shouldBeNull()
    }
}
