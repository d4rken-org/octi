package eu.darken.octi.main.ui.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.R
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.upgrade.UpgradeRepo
import io.kotest.matchers.shouldBe
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import kotlin.time.Instant
import eu.darken.octi.common.R as CommonR

class DashboardReviewCardRenderTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val bodyText: String
        get() = context.getString(R.string.review_app_body)

    private val laterAction: String
        get() = context.getString(CommonR.string.general_maybe_later_action)

    private val reviewAction: String
        get() = context.getString(R.string.review_app_review_action)

    private val upgradeInfo = object : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY
        override val isPro: Boolean = true
        override val isSettled: Boolean = true
        override val upgradedAt: Instant? = null
        override val error: Throwable? = null
    }

    @Composable
    private fun ReviewDashboard(
        onReviewLater: () -> Unit = {},
        onReview: () -> Unit = {},
    ) {
        DashboardScreen(
            state = DashboardVM.State(
                devices = emptyList(),
                deviceCount = 0,
                syncStatus = null,
                isOffline = false,
                showSyncSetup = false,
                showSyncedAlone = false,
                hasConnectors = true,
                missingPermissions = emptyList(),
                update = null,
                upgradeInfo = upgradeInfo,
                deviceLimitReached = false,
                showReviewCard = true,
            ),
            onRefresh = {},
            onSyncServices = {},
            onPlaceholderClick = {},
            onIssueClick = {},
            onConnectorDevices = {},
            onUpgrade = {},
            onSettings = {},
            onSnoozeSyncSetup = {},
            onSnoozeSyncedAlone = {},
            onSetupSync = {},
            onGrantPermission = {},
            onDismissPermission = {},
            onDismissUpdate = {},
            onViewUpdate = {},
            onStartUpdate = {},
            onToggleSyncExpanded = {},
            onToggleDeviceCollapsed = {},
            onPowerAlerts = {},
            onAppsList = {},
            onInstallLatestApp = {},
            onClearClipboard = {},
            onShareClipboard = {},
            onCopyClipboard = {},
            onFileShareClicked = {},
            onWifiPermissionGrant = {},
            onReviewLater = onReviewLater,
            onReview = onReview,
        )
    }

    @Test
    fun `the review card renders its body and both actions`() {
        composeRule.setContent {
            PreviewWrapper { ReviewDashboard() }
        }

        composeRule.onNodeWithText(bodyText).assertExists()
        composeRule.onNodeWithText(laterAction).assertExists()
        composeRule.onNodeWithText(reviewAction).assertExists()
    }

    @Test
    fun `a dismissed card ignores a later review tap`() {
        var laterTaps = 0
        var reviewTaps = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewDashboard(
                    onReviewLater = { laterTaps++ },
                    onReview = { reviewTaps++ },
                )
            }
        }

        // The card only disappears with the next state emission, so both targets are still on
        // screen: a review after a dismiss would re-open what the user just closed.
        composeRule.onNodeWithText(laterAction).performClick()
        composeRule.runOnIdle { laterTaps shouldBe 1 }

        composeRule.onNodeWithText(reviewAction).assertIsNotEnabled()
        composeRule.onNodeWithText(reviewAction).performClick()

        composeRule.runOnIdle {
            laterTaps shouldBe 1
            reviewTaps shouldBe 0
        }
    }

    @Test
    fun `a reviewed card ignores a later dismiss tap`() {
        var laterTaps = 0
        var reviewTaps = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewDashboard(
                    onReviewLater = { laterTaps++ },
                    onReview = { reviewTaps++ },
                )
            }
        }

        composeRule.onNodeWithText(reviewAction).performClick()
        composeRule.runOnIdle { reviewTaps shouldBe 1 }

        // A dismiss after a review would overwrite the completed-review bookkeeping with a snooze.
        composeRule.onNodeWithText(laterAction).assertIsNotEnabled()
        composeRule.onNodeWithText(laterAction).performClick()

        composeRule.runOnIdle {
            reviewTaps shouldBe 1
            laterTaps shouldBe 0
        }
    }

    @Test
    fun `repeated review taps are not absorbed by the card`() {
        var laterTaps = 0
        var reviewTaps = 0
        composeRule.setContent {
            PreviewWrapper {
                ReviewDashboard(
                    onReviewLater = { laterTaps++ },
                    onReview = { reviewTaps++ },
                )
            }
        }

        composeRule.onNodeWithText(reviewAction).performClick()
        composeRule.runOnIdle { reviewTaps shouldBe 1 }

        // A failed Play request persists nothing and leaves the card up, so the retry has to work.
        // Duplicates are the tool's problem, it holds a single-flight lock for exactly this.
        composeRule.onNodeWithText(reviewAction).assertIsEnabled()
        composeRule.onNodeWithText(reviewAction).performClick()

        composeRule.runOnIdle {
            reviewTaps shouldBe 2
            laterTaps shouldBe 0
        }
    }
}
