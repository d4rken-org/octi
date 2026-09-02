package eu.darken.octi.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * Crowdin tags translator screenshots by matching the source-language text in the image, so these
 * render en-US only. They cover screens the Play Store previews don't reach.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "en-US", locale = "en", device = DS)
annotation class CrowdinLocale

@PreviewTest
@CrowdinLocale
@Composable
fun SettingsIndex() = SettingsIndexContent()

@PreviewTest
@CrowdinLocale
@Composable
fun SupportSettings() = SupportSettingsContent()

@PreviewTest
@CrowdinLocale
@Composable
fun OnboardingWelcome() = OnboardingWelcomeContent()

@PreviewTest
@CrowdinLocale
@Composable
fun OnboardingPrivacy() = OnboardingPrivacyContent()

@PreviewTest
@CrowdinLocale
@Composable
fun PowerAlerts() = PowerAlertsContent()

@PreviewTest
@CrowdinLocale
@Composable
fun AddOctiServer() = AddOctiServerContent()

@PreviewTest
@CrowdinLocale
@Composable
fun OctiServerLinkHost() = OctiServerLinkHostContent()
