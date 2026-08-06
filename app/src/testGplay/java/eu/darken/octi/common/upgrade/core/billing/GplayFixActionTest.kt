package eu.darken.octi.common.upgrade.core.billing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import eu.darken.octi.R
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * The error dialog's "Google Play" button runs on an activity context: a device where the launch is
 * refused - or where there is nothing to launch - must get the failure reported through the dialog
 * (which can show the full message), not a clipped toast and not a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayFixActionTest : BaseTest() {

    /** Play is installed but unreachable: disabled app, restricted profile or a guarding ROM. */
    class DeniedLaunchActivity : Activity() {
        override fun startActivity(intent: Intent): Unit = throw SecurityException("Permission Denial")
    }

    /** No app settings screen resolves the intent at all, e.g. a stripped-down ROM. */
    class UnresolvedLaunchActivity : Activity() {
        override fun startActivity(intent: Intent): Unit = throw ActivityNotFoundException("No Activity found")
    }

    private val fixAction: (Activity) -> Unit
        get() = GplayServiceUnavailableException(RuntimeException("Play hiccup"))
            .getLocalizedError().fixAction.shouldNotBeNull()

    private fun <T : Activity> activityOf(clazz: Class<T>): T = Robolectric.buildActivity(clazz).setup().get()

    @Test
    fun `a denied launch reports through the dialog instead of a toast`() {
        val activity = activityOf(DeniedLaunchActivity::class.java)

        shouldThrow<SecurityException> { fixAction.invoke(activity) }

        // A toast caps at 2 lines and clipped this message (French lost a whole condition
        // mid-word) — the dialog renders it inline instead.
        ShadowToast.getLatestToast() shouldBe null
    }

    @Test
    fun `an unresolvable launch reports through the dialog instead of a toast`() {
        val activity = activityOf(UnresolvedLaunchActivity::class.java)

        shouldThrow<ActivityNotFoundException> { fixAction.invoke(activity) }

        ShadowToast.getLatestToast() shouldBe null
    }

    @Test
    fun `the failure message travels with the error for the dialog to show`() {
        val activity = activityOf(Activity::class.java)

        val message = GplayServiceUnavailableException(RuntimeException("Play hiccup"))
            .getLocalizedError().fixActionErrorMessage.shouldNotBeNull()

        message.get(activity) shouldBe activity.getString(R.string.upgrades_gplay_not_installed_message)
    }
}
