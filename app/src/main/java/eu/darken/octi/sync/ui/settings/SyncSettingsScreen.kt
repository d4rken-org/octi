package eu.darken.octi.sync.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Label
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.SignalWifi4Bar
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.Timer
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader
import eu.darken.octi.common.settings.SettingsSliderItem
import eu.darken.octi.common.settings.SettingsSwitchItem

private val BACKGROUND_PRESETS = listOf(15, 30, 60, 120, 240, 360, 720, 1440, 2160, 2880)
private val FOREGROUND_PRESETS = listOf(5, 10, 15, 30, 45, 60)

private fun Int.toNearestPresetIndex(presets: List<Int>): Int {
    val idx = presets.indexOf(this)
    if (idx >= 0) return idx
    return presets.indices.minByOrNull { kotlin.math.abs(presets[it] - this) } ?: 0
}

private fun formatInterval(minutes: Int): String = when {
    minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60}h"
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60} min"
    else -> "$minutes min"
}

@Composable
fun SyncSettingsScreenHost(vm: SyncSettingsVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        SyncSettingsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDeviceLabelChanged = { label -> vm.setDeviceLabel(label) },
            onBackgroundSyncEnabledChanged = { enabled -> vm.setBackgroundSyncEnabled(enabled) },
            onBackgroundSyncIntervalChanged = { minutes -> vm.setBackgroundSyncInterval(minutes) },
            onBackgroundSyncOnMobileChanged = { enabled -> vm.setBackgroundSyncOnMobile(enabled) },
            onChargingEnabledChanged = { enabled -> vm.setChargingEnabled(enabled) },
            onChargingIntervalChanged = { minutes -> vm.setChargingInterval(minutes) },
            onForegroundSyncEnabledChanged = { enabled -> vm.setForegroundSyncEnabled(enabled) },
            onForegroundSyncIntervalChanged = { minutes -> vm.setForegroundSyncInterval(minutes) },
        )
    }
}

@Composable
fun SyncSettingsScreen(
    state: SyncSettingsVM.State,
    onNavigateUp: () -> Unit,
    onDeviceLabelChanged: (String?) -> Unit,
    onBackgroundSyncEnabledChanged: (Boolean) -> Unit,
    onBackgroundSyncIntervalChanged: (Int) -> Unit,
    onBackgroundSyncOnMobileChanged: (Boolean) -> Unit,
    onChargingEnabledChanged: (Boolean) -> Unit,
    onChargingIntervalChanged: (Int) -> Unit,
    onForegroundSyncEnabledChanged: (Boolean) -> Unit,
    onForegroundSyncIntervalChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeviceLabelDialog by remember { mutableStateOf(false) }
    var showCustomIntervalDialog by remember { mutableStateOf<IntervalDialogConfig?>(null) }

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
            // Device label
            item {
                SettingsBaseItem(
                    title = stringResource(R.string.sync_setting_devicelabel_label),
                    subtitle = state.deviceLabel ?: stringResource(R.string.sync_setting_devicelabel_desc),
                    icon = Icons.AutoMirrored.TwoTone.Label,
                    onClick = { showDeviceLabelDialog = true },
                )
            }

            // Mobile network (general option)
            item {
                SettingsSwitchItem(
                    icon = Icons.TwoTone.SignalWifi4Bar,
                    title = stringResource(R.string.sync_setting_background_mobile_label),
                    subtitle = stringResource(R.string.sync_setting_mobile_desc),
                    checked = state.backgroundSyncOnMobile,
                    onCheckedChange = onBackgroundSyncOnMobileChanged,
                )
            }

            // Foreground sync section
            item {
                SettingsCategoryHeader(text = stringResource(R.string.sync_setting_category_foreground_label))
            }
            item {
                if (state.isPro) {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.Visibility,
                        title = stringResource(R.string.sync_setting_foreground_enable_title),
                        subtitle = stringResource(R.string.sync_setting_foreground_enable_desc),
                        checked = state.foregroundSyncEnabled,
                        onCheckedChange = onForegroundSyncEnabledChanged,
                    )
                } else {
                    SettingsBaseItem(
                        icon = Icons.TwoTone.Visibility,
                        title = stringResource(R.string.sync_setting_foreground_enable_title),
                        subtitle = stringResource(R.string.sync_setting_foreground_enable_desc),
                        onClick = { onForegroundSyncEnabledChanged(true) },
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
            if (state.foregroundSyncEnabled && state.isPro) {
                item {
                    IntervalSliderItem(
                        title = stringResource(R.string.sync_setting_foreground_interval_label),
                        presets = FOREGROUND_PRESETS,
                        currentValue = state.foregroundSyncInterval,
                        onIntervalChanged = onForegroundSyncIntervalChanged,
                        enabled = true,
                        minValue = 5,
                        maxValue = 60,
                        onShowCustomDialog = { showCustomIntervalDialog = it },
                    )
                }
            }

            // Background sync section
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
                IntervalSliderItem(
                    title = stringResource(R.string.sync_setting_interval_label),
                    presets = BACKGROUND_PRESETS,
                    currentValue = state.backgroundSyncInterval,
                    onIntervalChanged = onBackgroundSyncIntervalChanged,
                    enabled = state.backgroundSyncEnabled,
                    minValue = 15,
                    maxValue = 2880,
                    onShowCustomDialog = { showCustomIntervalDialog = it },
                )
            }
            item {
                if (state.isPro) {
                    SettingsSwitchItem(
                        icon = Icons.TwoTone.BatteryChargingFull,
                        title = stringResource(R.string.sync_setting_charging_enable_title),
                        subtitle = stringResource(R.string.sync_setting_charging_enable_desc),
                        checked = state.backgroundSyncChargingEnabled,
                        onCheckedChange = onChargingEnabledChanged,
                        enabled = state.backgroundSyncEnabled,
                    )
                } else {
                    SettingsBaseItem(
                        icon = Icons.TwoTone.BatteryChargingFull,
                        title = stringResource(R.string.sync_setting_charging_enable_title),
                        subtitle = stringResource(R.string.sync_setting_charging_enable_desc),
                        onClick = { onChargingEnabledChanged(true) },
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
            if (state.backgroundSyncChargingEnabled && state.isPro && state.backgroundSyncEnabled) {
                item {
                    IntervalSliderItem(
                        title = stringResource(R.string.sync_setting_charging_interval_label),
                        presets = BACKGROUND_PRESETS,
                        currentValue = state.backgroundSyncChargingInterval,
                        onIntervalChanged = onChargingIntervalChanged,
                        enabled = true,
                        minValue = 15,
                        maxValue = 2880,
                        onShowCustomDialog = { showCustomIntervalDialog = it },
                    )
                }
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

    showCustomIntervalDialog?.let { config ->
        CustomIntervalDialog(
            config = config,
            onDismiss = { showCustomIntervalDialog = null },
        )
    }
}

@Composable
private fun IntervalSliderItem(
    title: String,
    presets: List<Int>,
    currentValue: Int,
    onIntervalChanged: (Int) -> Unit,
    enabled: Boolean,
    minValue: Int,
    maxValue: Int,
    onShowCustomDialog: (IntervalDialogConfig) -> Unit,
) {
    var sliderIndex by remember(currentValue) {
        mutableStateOf(currentValue.toNearestPresetIndex(presets).toFloat())
    }

    SettingsSliderItem(
        icon = Icons.TwoTone.Timer,
        title = title,
        value = sliderIndex,
        onValueChange = { sliderIndex = it },
        onValueChangeFinished = { onIntervalChanged(presets[sliderIndex.toInt()]) },
        valueRange = 0f..(presets.size - 1).toFloat(),
        steps = presets.size - 2,
        enabled = enabled,
        valueLabel = { formatInterval(presets[it.toInt()]) },
        onValueLabelClick = {
            onShowCustomDialog(
                IntervalDialogConfig(
                    currentValue = currentValue,
                    minValue = minValue,
                    maxValue = maxValue,
                    onConfirm = onIntervalChanged,
                )
            )
        },
    )
}

private data class IntervalDialogConfig(
    val currentValue: Int,
    val minValue: Int,
    val maxValue: Int,
    val onConfirm: (Int) -> Unit,
)

@Composable
private fun CustomIntervalDialog(
    config: IntervalDialogConfig,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(config.currentValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sync_setting_interval_label)) },
        text = {
            Column {
                Text(
                    text = "${config.minValue}–${config.maxValue} min",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = text.toIntOrNull()?.coerceIn(config.minValue, config.maxValue) ?: config.currentValue
                    config.onConfirm(value)
                    onDismiss()
                },
            ) {
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
            isPro = true,
            backgroundSyncChargingEnabled = false,
            backgroundSyncChargingInterval = 15,
            foregroundSyncEnabled = false,
            foregroundSyncInterval = 5,
        ),
        onNavigateUp = {},
        onDeviceLabelChanged = {},
        onBackgroundSyncEnabledChanged = {},
        onBackgroundSyncIntervalChanged = {},
        onBackgroundSyncOnMobileChanged = {},
        onChargingEnabledChanged = {},
        onChargingIntervalChanged = {},
        onForegroundSyncEnabledChanged = {},
        onForegroundSyncIntervalChanged = {},
    )
}
