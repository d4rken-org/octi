package eu.darken.octi.modules.power.ui.alerts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Battery1Bar
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.power.core.alert.BatteryHighAlertRule
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.sync.core.DeviceId

@Composable
fun PowerAlertsScreenHost(
    deviceId: String,
    vm: PowerAlertsVM = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.initialize(deviceId) }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        PowerAlertsScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onLowBatteryThreshold = { threshold -> vm.setBatteryLowAlert(threshold) },
            onHighBatteryThreshold = { threshold -> vm.setBatteryHighAlert(threshold) },
        )
    }
}

@Composable
fun PowerAlertsScreen(
    state: PowerAlertsVM.State,
    onNavigateUp: () -> Unit,
    onLowBatteryThreshold: (Float) -> Unit,
    onHighBatteryThreshold: (Float) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(PowerR.string.module_power_alerts_title))
                        if (state.deviceLabel.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.device_x_label, state.deviceLabel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            LowBatteryCard(
                threshold = state.batteryLowAlert?.rule?.threshold ?: 0f,
                isActive = state.batteryLowAlert != null,
                onThresholdChanged = onLowBatteryThreshold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            HighBatteryCard(
                threshold = state.batteryHighAlert?.rule?.threshold ?: 0f,
                isActive = state.batteryHighAlert != null,
                onThresholdChanged = onHighBatteryThreshold,
            )
        }
    }
}

@Composable
private fun LowBatteryCard(
    threshold: Float,
    isActive: Boolean,
    onThresholdChanged: (Float) -> Unit,
) {
    var sliderValue by remember(threshold) { mutableFloatStateOf(threshold) }

    val caption = if (sliderValue == 0f) {
        stringResource(PowerR.string.module_power_alerts_lowbattery_disabled_caption)
    } else {
        stringResource(
            PowerR.string.module_power_alerts_lowbattery_slider_value_caption,
            "${(sliderValue * 100).toInt()}%",
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Battery1Bar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(PowerR.string.module_power_alerts_lowbattery_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Text(
                text = stringResource(PowerR.string.module_power_alerts_lowbattery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onThresholdChanged(sliderValue) },
                valueRange = 0f..0.95f,
                steps = 18,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun HighBatteryCard(
    threshold: Float,
    isActive: Boolean,
    onThresholdChanged: (Float) -> Unit,
) {
    var sliderValue by remember(threshold) { mutableFloatStateOf(threshold) }

    val caption = if (sliderValue == 0f) {
        stringResource(PowerR.string.module_power_alerts_highbattery_disabled_caption)
    } else {
        stringResource(
            PowerR.string.module_power_alerts_highbattery_slider_value_caption,
            "${(sliderValue * 100).toInt()}%",
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.BatteryFull,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(PowerR.string.module_power_alerts_highbattery_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Text(
                text = stringResource(PowerR.string.module_power_alerts_highbattery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onThresholdChanged(sliderValue) },
                valueRange = 0f..1f,
                steps = 19,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Preview2
@Composable
private fun PowerAlertsScreenActivePreview() = PreviewWrapper {
    val deviceId = DeviceId("preview-device")
    PowerAlertsScreen(
        state = PowerAlertsVM.State(
            deviceLabel = "Pixel 8",
            batteryLowAlert = PowerAlert(
                rule = BatteryLowAlertRule(deviceId = deviceId, threshold = 0.2f),
                event = null,
            ),
            batteryHighAlert = PowerAlert(
                rule = BatteryHighAlertRule(deviceId = deviceId, threshold = 0.9f),
                event = null,
            ),
        ),
        onNavigateUp = {},
        onLowBatteryThreshold = {},
        onHighBatteryThreshold = {},
    )
}

@Preview2
@Composable
private fun PowerAlertsScreenDisabledPreview() = PreviewWrapper {
    PowerAlertsScreen(
        state = PowerAlertsVM.State(deviceLabel = "Pixel 8"),
        onNavigateUp = {},
        onLowBatteryThreshold = {},
        onHighBatteryThreshold = {},
    )
}
