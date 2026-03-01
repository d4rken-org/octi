package eu.darken.octi.main.ui.settings.general

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Contrast
import androidx.compose.material.icons.twotone.DarkMode
import androidx.compose.material.icons.twotone.NewReleases
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsCategoryHeader
import eu.darken.octi.common.settings.SettingsListPreferenceItem
import eu.darken.octi.common.settings.SettingsSwitchItem
import android.os.Build
import eu.darken.octi.common.theming.ThemeColor
import eu.darken.octi.common.theming.ThemeMode
import eu.darken.octi.common.theming.ThemeStyle
import eu.darken.octi.common.R as CommonR

@Composable
fun GeneralSettingsScreenHost(vm: GeneralSettingsVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        GeneralSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onThemeModeSelected = { mode -> vm.setThemeMode(mode) },
            onThemeStyleSelected = { style -> vm.setThemeStyle(style) },
            onThemeColorSelected = { color -> vm.setThemeColor(color) },
            onUpdateCheckChanged = { enabled -> vm.setUpdateCheckEnabled(enabled) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    state: GeneralSettingsVM.State,
    onNavigateUp: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onThemeStyleSelected: (ThemeStyle) -> Unit,
    onThemeColorSelected: (ThemeColor) -> Unit,
    onUpdateCheckChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.general_settings_label)) },
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
            if (state.isUpdateCheckSupported) {
                item {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.NewReleases,
                        title = stringResource(R.string.updatecheck_setting_enabled_label),
                        subtitle = stringResource(R.string.updatecheck_setting_enabled_explanation),
                        checked = state.isUpdateCheckEnabled,
                        onCheckedChange = onUpdateCheckChanged,
                    )
                }
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_category_ui_label))
            }
            item {
                SettingsListPreferenceItem(
                    icon = Icons.TwoTone.DarkMode,
                    title = stringResource(CommonR.string.ui_theme_mode_setting_label),
                    entries = ThemeMode.entries,
                    selectedEntry = state.themeMode,
                    onEntrySelected = onThemeModeSelected,
                    entryLabel = { it.label.get(context) },
                    enabled = state.isPro,
                )
            }
            item {
                SettingsListPreferenceItem(
                    icon = Icons.TwoTone.Contrast,
                    title = stringResource(CommonR.string.ui_theme_style_setting_label),
                    entries = ThemeStyle.entries,
                    selectedEntry = state.themeStyle,
                    onEntrySelected = onThemeStyleSelected,
                    entryLabel = { it.label.get(context) },
                    enabled = state.isPro,
                )
            }
            item {
                val isColorPickerEnabled = state.isPro &&
                    !(state.themeStyle == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                SettingsListPreferenceItem(
                    icon = Icons.TwoTone.Palette,
                    title = stringResource(CommonR.string.ui_theme_color_setting_label),
                    entries = ThemeColor.entries,
                    selectedEntry = state.themeColor,
                    onEntrySelected = onThemeColorSelected,
                    entryLabel = { it.label.get(context) },
                    enabled = isColorPickerEnabled,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun GeneralSettingsScreenPreview() = PreviewWrapper {
    GeneralSettingsScreen(
        state = GeneralSettingsVM.State(
            isPro = true,
            isUpdateCheckSupported = true,
            isUpdateCheckEnabled = true,
            themeMode = ThemeMode.SYSTEM,
            themeStyle = ThemeStyle.DEFAULT,
            themeColor = ThemeColor.GREEN,
        ),
        onNavigateUp = {},
        onThemeModeSelected = {},
        onThemeStyleSelected = {},
        onThemeColorSelected = {},
        onUpdateCheckChanged = {},
    )
}
