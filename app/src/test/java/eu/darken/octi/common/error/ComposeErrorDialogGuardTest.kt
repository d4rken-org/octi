package eu.darken.octi.common.error

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.flow.SingleEventFlow
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import eu.darken.octi.common.R as CommonR

/**
 * The error dialog runs whatever action an error hands it: a throwing action must neither take the
 * UI down with it nor leave the dialog latched on the error it was supposed to resolve.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ComposeErrorDialogGuardTest : BaseTest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private class FakeErrorSource : ErrorEventSource {
        override val errorEvents = SingleEventFlow<Throwable>()
    }

    private class ThrowingFixError(
        private val onFix: () -> Unit,
        private val fixErrorMessage: String? = null,
    ) : Exception("fix error"), HasLocalizedError {
        override fun getLocalizedError(): LocalizedError = LocalizedError(
            throwable = this,
            label = "Fix error".toCaString(),
            description = "Something wants fixing".toCaString(),
            fixActionLabel = FIX_LABEL.toCaString(),
            fixAction = { onFix() },
            fixActionErrorMessage = fixErrorMessage?.toCaString(),
        )
    }

    private class InfoActionError(
        private val onInfo: () -> Unit,
        private val fixErrorMessage: String? = null,
    ) : Exception("info error"), HasLocalizedError {
        override fun getLocalizedError(): LocalizedError = LocalizedError(
            throwable = this,
            label = "Info error".toCaString(),
            description = "Something needs a closer look".toCaString(),
            fixActionErrorMessage = fixErrorMessage?.toCaString(),
            infoAction = { onInfo() },
        )
    }

    private fun showError(error: Throwable) {
        val source = FakeErrorSource()
        composeRule.setContent {
            PreviewWrapper {
                ErrorEventHandler(source)
            }
        }
        // Buffered channel: the event survives until the handler's collector attaches.
        source.errorEvents.tryEmit(error)
        composeRule.waitForIdle()
    }

    @Test
    fun `a throwing fix action still dismisses the dialog`() {
        var invoked = false
        showError(
            ThrowingFixError(
                onFix = {
                    // Flag first: the assertion below has to distinguish "action ran and threw" from
                    // "action was never dispatched".
                    invoked = true
                    throw IllegalStateException("fix action exploded")
                },
            )
        )

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        invoked shouldBe true
        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
    }

    @Test
    fun `a throwing info action still dismisses the dialog`() {
        // The other dispatch site: no fix to offer, but the details action is just as arbitrary.
        var infoClicks = 0
        showError(
            InfoActionError(
                onInfo = {
                    infoClicks++
                    throw IllegalStateException("info action exploded")
                },
            )
        )

        composeRule.onNodeWithText(context.getString(CommonR.string.general_show_details_action)).performClick()
        composeRule.waitForIdle()

        infoClicks shouldBe 1
        composeRule.onAllNodesWithText(context.getString(android.R.string.ok)).assertCountEquals(0)
    }

    @Test
    fun `a throwing fix action with its own message keeps the dialog open and shows it inline`() {
        // A Toast caps at 2 lines and clipped this kind of message; the dialog body has no cap.
        showError(
            ThrowingFixError(
                onFix = { throw IllegalStateException("fix action exploded") },
                fixErrorMessage = FIX_ERROR_MESSAGE,
            )
        )

        composeRule.onNodeWithText(FIX_LABEL).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FIX_ERROR_MESSAGE).assertExists()
        composeRule.onNodeWithText(FIX_LABEL).assertExists()
        // Not latched: the way out stays available while the message is shown.
        composeRule.onNodeWithText(context.getString(CommonR.string.general_dismiss_action)).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(FIX_LABEL).assertCountEquals(0)
        composeRule.onAllNodesWithText(FIX_ERROR_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `a throwing info action never borrows the fix action's failure message`() {
        // The failure copy belongs to the fix action's dispatch, not to the error: the info button
        // dispatches without one and must keep the plain log-then-dismiss behaviour. Octi never
        // renders both buttons at once, so the message rides on an info-only error here — which is
        // exactly the shape that would break if the dialog read it off the error instead.
        var infoClicks = 0
        showError(
            InfoActionError(
                onInfo = {
                    infoClicks++
                    throw IllegalStateException("info action exploded")
                },
                fixErrorMessage = FIX_ERROR_MESSAGE,
            )
        )

        composeRule.onNodeWithText(context.getString(CommonR.string.general_show_details_action)).performClick()
        composeRule.waitForIdle()

        infoClicks shouldBe 1
        composeRule.onAllNodesWithText(context.getString(android.R.string.ok)).assertCountEquals(0)
        composeRule.onAllNodesWithText(FIX_ERROR_MESSAGE).assertCountEquals(0)
    }
}

private const val FIX_LABEL = "Boom"
private const val FIX_ERROR_MESSAGE = "Fixing it did not work"
