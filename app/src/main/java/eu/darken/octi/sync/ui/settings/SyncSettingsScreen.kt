package eu.darken.octi.sync.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Label
import androidx.compose.material.icons.twotone.SignalWifi4Bar
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader
import eu.darken.octi.common.settings.SettingsSliderItem
import eu.darken.octi.common.settings.SettingsSwitchItem

@Composable
fun SyncSettingsScreenHost(vm: SyncSettingsVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        SyncSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDeviceLabelChanged = { label -> vm.setDeviceLabel(label) },
            onBackgroundSyncEnabledChanged = { enabled -> vm.setBackgroundSyncEnabled(enabled) },
            onBackgroundSyncIntervalChanged = { minutes -> vm.setBackgroundSyncInterval(minutes) },
            onBackgroundSyncOnMobileChanged = { enabled -> vm.setBackgroundSyncOnMobile(enabled) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    state: SyncSettingsVM.State,
    onNavigateUp: () -> Unit,
    onDeviceLabelChanged: (String?) -> Unit,
    onBackgroundSyncEnabledChanged: (Boolean) -> Unit,
    onBackgroundSyncIntervalChanged: (Int) -> Unit,
    onBackgroundSyncOnMobileChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeviceLabelDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sync_settings_label)) },
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
                    title = stringResource(R.string.sync_setting_devicelabel_label),
                    subtitle = state.deviceLabel ?: stringResource(R.string.sync_setting_devicelabel_desc),
                    icon = Icons.AutoMirrored.TwoTone.Label,
                    onClick = { showDeviceLabelDialog = true },
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.sync_setting_category_background_label))
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.Sync,
                    title = stringResource(R.string.sync_setting_background_enable_title),
                    subtitle = stringResource(R.string.sync_setting_background_enable_desc),
                    checked = state.backgroundSyncEnabled,
                    onCheckedChange = onBackgroundSyncEnabledChanged,
                )
            }
            item {
                SettingsSliderItem(
                    icon = Icons.TwoTone.Timer,
                    title = stringResource(R.string.sync_setting_interval_label),
                    value = state.backgroundSyncInterval.toFloat(),
                    onValueChange = { onBackgroundSyncIntervalChanged(it.toInt()) },
                    valueRange = 15f..1440f,
                    steps = ((1440 - 15) / 15) - 1,
                    enabled = state.backgroundSyncEnabled,
                    valueLabel = { "${it.toInt()} min" },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.SignalWifi4Bar,
                    title = stringResource(R.string.sync_setting_background_mobile_label),
                    subtitle = stringResource(R.string.sync_setting_background_mobile_desc),
                    checked = state.backgroundSyncOnMobile,
                    onCheckedChange = onBackgroundSyncOnMobileChanged,
                    enabled = state.backgroundSyncEnabled,
                )
            }
        }
    }

    if (showDeviceLabelDialog) {
        DeviceLabelDialog(
            currentLabel = state.deviceLabel.orEmpty(),
            onConfirm = { label ->
                onDeviceLabelChanged(label)
                showDeviceLabelDialog = false
            },
            onDismiss = { showDeviceLabelDialog = false },
        )
    }
}

@Composable
private fun DeviceLabelDialog(
    currentLabel: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sync_setting_devicelabel_label)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.sync_setting_devicelabel_hint),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.ifBlank { null }) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Preview2
@Composable
private fun SyncSettingsScreenPreview() = PreviewWrapper {
    SyncSettingsScreen(
        state = SyncSettingsVM.State(
            deviceLabel = null,
            backgroundSyncEnabled = true,
            backgroundSyncInterval = 60,
            backgroundSyncOnMobile = true,
        ),
        onNavigateUp = {},
        onDeviceLabelChanged = {},
        onBackgroundSyncEnabledChanged = {},
        onBackgroundSyncIntervalChanged = {},
        onBackgroundSyncOnMobileChanged = {},
    )
}
