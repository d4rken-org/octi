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
import androidx.compose.ui.text.AnnotatedString
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
        // The pitch leads with the hero card: mascot and preamble in one card, no standalone header.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.HERO).assertCountEquals(1)
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
        // The status views keep the standalone mascot header — the hero card belongs to the pitch.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.HERO).assertCountEquals(0)
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
        composeRule.onAllNodesWithTag(UpgradeScreenTags.HERO).assertCountEquals(0)
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
     * "FOSS" is the flavor's name, not prose, and the FOSS build must never pick up a translated
     * copy of it — that regression shipped once already, when the postfix lived in the shared
     * resource file and the merger handed FOSS a locale's translated value.
     *
     * These resolve through the real resource merger. They assert span boundaries rather than token
     * counts: the branch this replaced coloured the second of exactly two space-separated tokens,
     * so a locale that composed its title differently either lost the flavor name entirely or put
     * the highlight on the wrong word while still rendering correct-looking text.
     *
     * The expected text is *derived* from the resolved template rather than hardcoded. Arrangement
     * is a language property, not a flavor one — the template lives in `app-common` and is
     * translatable, so a locale may legitimately reorder or repunctuate the title, and FOSS
     * inherits that. What must not vary is the qualifier itself.
     */
    @Test
    @Config(qualifiers = "fr")
    fun `the flavor qualifier is not translated in french`() {
        val qualifier = context.getString(R.string.app_name_upgrade_postfix)
        qualifier shouldBe "FOSS"

        val result = captureBrandTitle()

        result.text shouldBe composedTitle(qualifier)
        result.spanStyles.size shouldBe 1
        val span = result.spanStyles.single()
        result.text.substring(span.start, span.end) shouldBe qualifier
    }

    /**
     * Arabic is the one locale that translates `app_name`, so the composed FOSS title localizes the
     * brand while the flavor qualifier stays pinned to "FOSS". That is the title the FOSS upgrade
     * screen already rendered here — the dashboard used to disagree with it by reading an atomic,
     * non-translatable composed string.
     */
    @Test
    @Config(qualifiers = "ar")
    fun `the flavor qualifier is not translated in arabic`() {
        val qualifier = context.getString(R.string.app_name_upgrade_postfix)
        qualifier shouldBe "FOSS"
        // The reason this locale is worth its own case: it is the only one that localizes the brand.
        context.getString(CommonR.string.app_name) shouldBe "أوكتي"

        val result = captureBrandTitle()

        result.text shouldBe composedTitle(qualifier)
        result.spanStyles.size shouldBe 1
        val span = result.spanStyles.single()
        result.text.substring(span.start, span.end) shouldBe qualifier
    }

    /** The title the current locale's template composes — whatever order it puts the parts in. */
    private fun composedTitle(qualifier: String): String = context.getString(
        CommonR.string.app_name_upgraded_template,
        context.getString(CommonR.string.app_name),
        qualifier,
    )

    private fun captureBrandTitle(): AnnotatedString {
        lateinit var captured: AnnotatedString
        composeRule.setUpgradeContent {
            captured = brandTitle(includeQualifier = true, highlightQualifier = true)
        }
        composeRule.waitForIdle()
        return captured
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
