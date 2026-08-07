package eu.darken.octi.main.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import eu.darken.octi.R
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.upgrade.ui.upgradeScreenTitle
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.octi.common.R as CommonR

/**
 * The Settings upgrade row used to render its own hardcoded per-flavor brand string, which
 * drifted from the composed title the upgrade screen renders. Now both surfaces compose through
 * the same template, and this test is the regression guard: the row must show exactly the text
 * the upgrade screen's title resolves to.
 *
 * Expectations derive from the resolved template rather than pinned literals, so the test holds
 * for both flavors and keeps holding when translations change the wording or word order.
 *
 * The base (English) values of the retired resource happened to equal the composed title, so a
 * default-locale check alone could not catch a revert to a resource-backed row. The extra locale
 * cases pick translations where the two visibly diverged — Swedish's qualifier is not "Pro" on
 * GPLAY, and Arabic translates the app name itself — one `setContent` per test, since the rule
 * allows only one per test method.
 */
class SettingsUpgradeRowTest : BaseComposeRobolectricTest() {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val composed: String
        get() = context.getString(
            CommonR.string.app_name_upgraded_template,
            context.getString(CommonR.string.app_name),
            context.getString(R.string.app_name_upgrade_postfix),
        )

    private fun assertRowAgreesWithUpgradeTitle() {
        lateinit var titleUpgraded: AnnotatedString
        lateinit var titleFree: AnnotatedString
        composeRule.setContent {
            PreviewWrapper {
                titleUpgraded = upgradeScreenTitle(upgraded = true)
                titleFree = upgradeScreenTitle(upgraded = false)
                SettingsIndexScreen(
                    state = SettingsIndexVM.State(),
                    onNavigateUp = {},
                    onGeneralSettings = {},
                    onSyncSettings = {},
                    onModuleSettings = {},
                    onSupport = {},
                    onChangelog = {},
                    onEcosystem = {},
                    onHelpTranslate = {},
                    onAcknowledgements = {},
                    onPrivacyPolicy = {},
                    onUpgradeStatus = {},
                )
            }
        }
        composeRule.waitForIdle()

        // The upgrade screen's title is the composed template — in both of its states.
        titleUpgraded.text shouldBe composed
        titleFree.text shouldBe composed

        // And the Settings row renders that same composition, exactly once. A row that re-grew its
        // own literal would no longer match the resolved template and fail here.
        composeRule.onAllNodesWithText(composed).assertCountEquals(1)
    }

    @Test
    fun `the upgrade row agrees with the upgrade screen title`() {
        assertRowAgreesWithUpgradeTitle()
    }

    // Swedish translates the GPLAY qualifier to something other than "Pro", so this locale is one
    // where the retired hardcoded row visibly disagreed with the composed title.
    @Test
    fun `the upgrade row agrees with the upgrade screen title in Swedish`() {
        RuntimeEnvironment.setQualifiers("+sv")
        assertRowAgreesWithUpgradeTitle()
    }

    // Arabic translates the app name itself, so agreement here proves the row composes from the
    // localized brand rather than any Latin-script literal — in both flavors.
    @Test
    fun `the upgrade row agrees with the upgrade screen title in Arabic`() {
        RuntimeEnvironment.setQualifiers("+ar")
        assertRowAgreesWithUpgradeTitle()
    }
}
