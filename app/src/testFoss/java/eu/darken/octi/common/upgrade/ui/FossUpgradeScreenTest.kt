package eu.darken.octi.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.R
import eu.darken.octi.common.compose.PreviewWrapper
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.annotation.Config
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Instant
import eu.darken.octi.common.R as CommonR

class FossUpgradeScreenTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val upgradedTitle: String
        get() = "${context.getString(CommonR.string.app_name)} ${context.getString(R.string.app_name_upgrade_postfix)}"

    @Test
    fun `renders the pitch content without duplicated app bar title`() {
        composeRule.setUpgradeContent {
            UpgradeScreen()
        }

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_why_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(firstFeatureLine(context, R.string.upgrade_screen_benefits_body))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_sponsor_action_hint))
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
    }

    @Test
    fun `sponsor button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(onGithubSponsors = { clicked = true })
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SPONSOR).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle { clicked shouldBe true }
    }

    @Test
    fun `free status view shows the status without any pitch content`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_FREE)
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_FREE).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(upgradedTitle).assertCountEquals(0)
    }

    @Test
    fun `upgrade options button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_FREE, onShowUpgradeOptions = { clicked = true })
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle { clicked shouldBe true }
    }

    @Test
    fun `upgraded status view thanks the supporter and offers a recurring donation`() {
        val since = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, supporterSince = since)
        }

        // Literal on purpose: the resource-derived helper would follow a wrong postfix along. FOSS
        // has no "Pro" to sell, so the upgraded title has to read "Octi FOSS".
        composeRule.onAllNodesWithText("Octi FOSS").assertCountEquals(1)
        composeRule.onAllNodesWithText(upgradedTitle).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_supporter_body))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(
            context.getString(R.string.upgrade_screen_supporter_since, formatted(since))
        ).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_DONATE).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SHOW_OPTIONS).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(0)
    }

    @Test
    fun `the supporter-since line stays away without a date`() {
        // UpgradeRepoFoss can report an upgrade whose timestamp predates the field: no date line
        // instead of a bogus one.
        composeRule.setUpgradeContent {
            UpgradeScreen(view = FossUpgradeView.STATUS_UPGRADED, supporterSince = null)
        }

        composeRule.onAllNodesWithText(
            context.getString(
                R.string.upgrade_screen_supporter_since,
                formatted(Instant.fromEpochMilliseconds(0L)),
            )
        ).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_STATUS_UPGRADED).assertCountEquals(1)
    }

    @Test
    fun `the recurring donation button is wired to the unarmed sponsors entrypoint`() {
        // Arming it would re-run the unlock on return and rewrite the supporter-since date shown
        // right above it.
        var armed = false
        var unarmed = false

        composeRule.setUpgradeContent {
            UpgradeScreen(
                view = FossUpgradeView.STATUS_UPGRADED,
                onGithubSponsors = { armed = true },
                onOpenSponsors = { unarmed = true },
            )
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_DONATE)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            unarmed shouldBe true
            armed shouldBe false
        }
    }

    /**
     * "FOSS" is the flavor's name, not prose. French and Arabic used to translate the upgraded app
     * name, which also broke DashboardScreen's two-tone title: it colours the second of exactly two
     * space-separated tokens, and "Octi, version libre" / the four-token Arabic variant fall out of
     * that branch entirely. These resolve through the real resource merger, so a stray locale copy
     * coming back would fail here.
     */
    @Test
    @Config(qualifiers = "fr")
    fun `the upgraded app name is not translated in french`() {
        context.getString(R.string.app_name_upgraded) shouldBe "Octi FOSS"
        context.getString(R.string.app_name_upgrade_postfix) shouldBe "FOSS"
        context.getString(R.string.app_name_upgraded).split(" ").size shouldBe 2
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the upgraded app name is not translated in arabic`() {
        context.getString(R.string.app_name_upgraded) shouldBe "Octi FOSS"
        context.getString(R.string.app_name_upgrade_postfix) shouldBe "FOSS"
        context.getString(R.string.app_name_upgraded).split(" ").size shouldBe 2
    }
}

private fun formatted(instant: Instant): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())
    .format(java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()))

private fun ComposeContentTestRule.setUpgradeContent(
    content: @Composable () -> Unit,
) {
    setContent {
        PreviewWrapper {
            content()
        }
    }
}

private fun firstFeatureLine(context: Context, resId: Int): String = context.getString(resId)
    .lineSequence()
    .map { it.trim() }
    .first { it.startsWith("•") }
    .removePrefix("•")
    .trim()
