package eu.darken.octi.screenshots

import androidx.compose.runtime.Composable
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.main.ui.onboarding.privacy.PrivacyScreen
import eu.darken.octi.main.ui.onboarding.privacy.PrivacyVM
import eu.darken.octi.main.ui.onboarding.welcome.WelcomeScreen
import eu.darken.octi.main.ui.settings.SettingsIndexScreen
import eu.darken.octi.main.ui.settings.SettingsIndexVM
import eu.darken.octi.main.ui.settings.support.SupportScreen
import eu.darken.octi.main.ui.settings.support.SupportVM
import eu.darken.octi.modules.power.core.alert.BatteryHighAlertRule
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.ui.alerts.PowerAlertsScreen
import eu.darken.octi.modules.power.ui.alerts.PowerAlertsVM
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.syncs.octiserver.core.OctiServer
import eu.darken.octi.syncs.octiserver.ui.add.AddOctiServerScreen
import eu.darken.octi.syncs.octiserver.ui.add.AddOctiServerVM
import eu.darken.octi.syncs.octiserver.ui.link.OctiServerLinkOption
import eu.darken.octi.syncs.octiserver.ui.link.host.OctiServerLinkHostScreen
import eu.darken.octi.syncs.octiserver.ui.link.host.OctiServerLinkHostVM

private val ALERT_DEVICE = DeviceId("pixel-8")

/** Same length as a real link code, so the preview shows the layout at the size it actually renders. */
private val LINK_CODE = "H4sIAAAAAAAAA" + "Wm9jdGlwcmV2aWV3".repeat(30) + "AAA=="

@Composable
internal fun SettingsIndexContent() = PreviewWrapper {
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

@Composable
internal fun SupportSettingsContent() = PreviewWrapper {
    SupportScreen(
        state = SupportVM.State(isRecording = false, currentLogPath = null),
        onNavigateUp = {},
        onDocumentation = {},
        onIssueTracker = {},
        onDiscord = {},
        onStartDebugLog = {},
        onStopDebugLog = {},
        onContactDeveloper = {},
        onDeleteAllLogs = {},
        onDeleteSession = {},
        onOpenSession = {},
        onDismissShortRecordingWarning = {},
        onForceStopDebugLog = {},
        onOpenPrivacyPolicy = {},
    )
}

@Composable
internal fun OnboardingWelcomeContent() = PreviewWrapper {
    WelcomeScreen(showBetaHint = true, onContinue = {})
}

@Composable
internal fun OnboardingPrivacyContent() = PreviewWrapper {
    PrivacyScreen(
        state = PrivacyVM.State(
            isUpdateCheckSupported = true,
            isUpdateCheckEnabled = true,
        ),
        onPrivacyPolicy = {},
        onToggleUpdateCheck = {},
        onContinue = {},
    )
}

@Composable
internal fun PowerAlertsContent() = PreviewWrapper {
    PowerAlertsScreen(
        state = PowerAlertsVM.State(
            deviceLabel = "Pixel 8",
            isPro = true,
            batteryLowAlert = PowerAlert(
                rule = BatteryLowAlertRule(deviceId = ALERT_DEVICE, threshold = 0.2f),
                event = null,
            ),
            batteryHighAlert = PowerAlert(
                rule = BatteryHighAlertRule(deviceId = ALERT_DEVICE, threshold = 0.9f),
                event = null,
            ),
        ),
        onNavigateUp = {},
        onLowBatteryThreshold = {},
        onHighBatteryThreshold = {},
        onUpgrade = {},
    )
}

@Composable
internal fun AddOctiServerContent() = PreviewWrapper {
    AddOctiServerScreen(
        state = AddOctiServerVM.State(serverType = OctiServer.Official.PROD),
        onNavigateUp = {},
        onSelectType = {},
        onCreateAccount = {},
    )
}

@Composable
internal fun OctiServerLinkHostContent() = PreviewWrapper {
    OctiServerLinkHostScreen(
        state = OctiServerLinkHostVM.State(
            linkOption = OctiServerLinkOption.DIRECT,
            encodedLinkCode = LINK_CODE,
        ),
        onNavigateUp = {},
        onLinkOptionSelected = {},
        onShareLinkCode = {},
        onCopyLinkCode = {},
    )
}
