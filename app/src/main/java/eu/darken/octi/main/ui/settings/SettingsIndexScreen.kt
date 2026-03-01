package eu.darken.octi.main.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Book
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SupportAgent
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader

@Composable
fun SettingsIndexScreenHost(vm: SettingsIndexVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        SettingsIndexScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onGeneralSettings = { vm.navTo(Nav.Settings.General) },
            onSyncSettings = { vm.navTo(Nav.Settings.Sync) },
            onModuleSettings = { vm.navTo(Nav.Settings.Modules) },
            onSupport = { vm.navTo(Nav.Settings.Support) },
            onChangelog = { vm.openUrl("https://octi.darken.eu/changelog") },
            onHelpTranslate = { vm.openUrl("https://crowdin.com/project/octi") },
            onAcknowledgements = { vm.navTo(Nav.Settings.Acknowledgements) },
            onPrivacyPolicy = { vm.openUrl(PrivacyPolicy.URL) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsIndexScreen(
    state: SettingsIndexVM.State,
    onNavigateUp: () -> Unit,
    onGeneralSettings: () -> Unit,
    onSyncSettings: () -> Unit,
    onModuleSettings: () -> Unit,
    onSupport: () -> Unit,
    onChangelog: () -> Unit,
    onHelpTranslate: () -> Unit,
    onAcknowledgements: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.label_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.general_settings_label),
                    subtitle = stringResource(R.string.general_settings_desc),
                    icon = Icons.TwoTone.Settings,
                    onClick = onGeneralSettings,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.sync_settings_label),
                    subtitle = stringResource(R.string.sync_settings_desc),
                    icon = Icons.TwoTone.Sync,
                    onClick = onSyncSettings,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.modules_settings_label),
                    subtitle = stringResource(R.string.modules_settings_desc),
                    icon = Icons.TwoTone.Extension,
                    onClick = onModuleSettings,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label))
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.changelog_label),
                    subtitle = BuildConfigWrap.VERSION_DESCRIPTION,
                    iconPainter = painterResource(R.drawable.ic_changelog_onsurface),
                    onClick = onChangelog,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_support_label),
                    subtitle = "\u00AF\\_(ツ)_/\u00AF",
                    icon = Icons.TwoTone.SupportAgent,
                    onClick = onSupport,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.help_translate_label),
                    subtitle = stringResource(R.string.help_translate_description),
                    icon = Icons.TwoTone.Translate,
                    onClick = onHelpTranslate,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_acknowledgements_label),
                    subtitle = stringResource(R.string.general_thank_you_label),
                    icon = Icons.TwoTone.Favorite,
                    onClick = onAcknowledgements,
                )
            }
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.settings_privacy_policy_label),
                    subtitle = stringResource(R.string.settings_privacy_policy_desc),
                    icon = Icons.TwoTone.Book,
                    onClick = onPrivacyPolicy,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SettingsIndexScreenPreview() = PreviewWrapper {
    SettingsIndexScreen(
        state = SettingsIndexVM.State(),
        onNavigateUp = {},
        onGeneralSettings = {},
        onSyncSettings = {},
        onModuleSettings = {},
        onSupport = {},
        onChangelog = {},
        onHelpTranslate = {},
        onAcknowledgements = {},
        onPrivacyPolicy = {},
    )
}
