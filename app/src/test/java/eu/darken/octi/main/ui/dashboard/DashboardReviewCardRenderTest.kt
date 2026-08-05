package eu.darken.octi.main.ui.dashboard

import android.content.Context
import androidx.compose.runtime.Composable
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
    fun `both review card actions report back`() {
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

        composeRule.onNodeWithText(laterAction).performClick()
        composeRule.onNodeWithText(reviewAction).performClick()

        composeRule.runOnIdle {
            laterTaps shouldBe 1
            reviewTaps shouldBe 1
        }
    }
}
