package eu.darken.octi.module.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Public
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Store
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader
import eu.darken.octi.common.settings.SettingsSwitchItem

@Composable
fun ModuleSettingsScreenHost(vm: ModuleSettingsVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        ModuleSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onPowerEnabledChanged = { enabled -> vm.setPowerEnabled(enabled) },
            onConnectivityEnabledChanged = { enabled -> vm.setConnectivityEnabled(enabled) },
            onWifiEnabledChanged = { enabled -> vm.setWifiEnabled(enabled) },
            onAppsEnabledChanged = { enabled -> vm.setAppsEnabled(enabled) },
            onAppsInstallerEnabledChanged = { enabled -> vm.setAppsInstallerEnabled(enabled) },
            onUpgrade = { vm.goUpgrade() },
            onClipboardEnabledChanged = { enabled -> vm.setClipboardEnabled(enabled) },
        )
    }
}

@Composable
fun ModuleSettingsScreen(
    state: ModuleSettingsVM.State,
    onNavigateUp: () -> Unit,
    onPowerEnabledChanged: (Boolean) -> Unit,
    onConnectivityEnabledChanged: (Boolean) -> Unit,
    onWifiEnabledChanged: (Boolean) -> Unit,
    onAppsEnabledChanged: (Boolean) -> Unit,
    onAppsInstallerEnabledChanged: (Boolean) -> Unit,
    onUpgrade: () -> Unit,
    onClipboardEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.modules_settings_label)) },
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
                SettingsCategoryHeader(text = stringResource(R.string.modules_category_energy_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.BatteryFull,
                    title = stringResource(PowerR.string.module_power_label),
                    subtitle = stringResource(PowerR.string.module_power_desc),
                    checked = state.isPowerEnabled,
                    onCheckedChange = onPowerEnabledChanged,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.modules_category_connectivity_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Public,
                    title = stringResource(ConnectivityR.string.module_connectivity_label),
                    subtitle = stringResource(ConnectivityR.string.module_connectivity_desc),
                    checked = state.isConnectivityEnabled,
                    onCheckedChange = onConnectivityEnabledChanged,
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Wifi,
                    title = stringResource(WifiR.string.module_wifi_label),
                    subtitle = stringResource(WifiR.string.module_wifi_desc),
                    checked = state.isWifiEnabled,
                    onCheckedChange = onWifiEnabledChanged,
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.module_apps_category_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Apps,
                    title = stringResource(AppsR.string.module_apps_label),
                    subtitle = stringResource(AppsR.string.module_apps_desc),
                    checked = state.isAppsEnabled,
                    onCheckedChange = onAppsEnabledChanged,
                )
            }
            item {
                if (state.isPro) {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.Store,
                        title = stringResource(AppsR.string.module_apps_installer_label),
                        subtitle = stringResource(AppsR.string.module_apps_installer_desc),
                        checked = state.isAppsInstallerEnabled,
                        onCheckedChange = onAppsInstallerEnabledChanged,
                        enabled = state.isAppsEnabled,
                    )
                } else {
                    SettingsBaseItem(
                        icon = Icons.TwoTone.Store,
                        title = stringResource(AppsR.string.module_apps_installer_label),
                        subtitle = stringResource(AppsR.string.module_apps_installer_desc),
                        onClick = onUpgrade,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.TwoTone.Stars,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        },
                    )
                }
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.modules_category_misc_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.ContentPaste,
                    title = stringResource(ClipboardR.string.module_clipboard_label),
                    subtitle = stringResource(ClipboardR.string.module_clipboard_desc),
                    checked = state.isClipboardEnabled,
                    onCheckedChange = onClipboardEnabledChanged,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun ModuleSettingsScreenPreview() = PreviewWrapper {
    ModuleSettingsScreen(
        state = ModuleSettingsVM.State(
            isPro = true,
            isPowerEnabled = true,
            isConnectivityEnabled = true,
            isWifiEnabled = true,
            isAppsEnabled = true,
            isAppsInstallerEnabled = false,
            isClipboardEnabled = true,
        ),
        onNavigateUp = {},
        onPowerEnabledChanged = {},
        onConnectivityEnabledChanged = {},
        onWifiEnabledChanged = {},
        onAppsEnabledChanged = {},
        onAppsInstallerEnabledChanged = {},
        onUpgrade = {},
        onClipboardEnabledChanged = {},
    )
}
