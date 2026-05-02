package eu.darken.octi.modules.power.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Battery1Bar
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.NotificationsNone
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.core.alert.BatteryHighAlertRule
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter
import eu.darken.octi.modules.power.ui.batteryIcon
import eu.darken.octi.sync.core.DeviceId

val LocalPowerTemperatureUnit =
    staticCompositionLocalOf<TemperatureFormatter.TemperatureUnit?> { null }

@Composable
fun PowerModuleTile(
    state: PowerDashState,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onDetailClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    val power = state.info
    val percent = (power.battery.percent * 100).toInt()
    val lowAlert = state.batteryLowAlert
    val isAlertActive = lowAlert?.triggeredAt != null && lowAlert.dismissedAt == null
    val statusText = powerTileStatusText(power)

    val tileColor = when {
        isAlertActive || power.battery.percent < 0.2f && !power.isCharging -> {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        }
        power.isCharging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        isWide -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val tileDescription = "$percent% $statusText"

    Surface(
        onClick = onDetailClicked,
        modifier = modifier.semantics { contentDescription = tileDescription },
        shape = RoundedCornerShape(12.dp),
        color = tileColor,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Icon + metric + actions on same row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = power.batteryIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isAlertActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isAlertActive) FontWeight.Bold else FontWeight.Normal,
                    color = if (isAlertActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (lowAlert != null || state.batteryHighAlert != null) {
                    Icon(
                        imageVector = Icons.TwoTone.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (state.showSettings) {
                    IconButton(onClick = onSettingsClicked, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val progressColor = when {
                isAlertActive || power.battery.percent < 0.2f -> MaterialTheme.colorScheme.error
                power.battery.percent < 0.4f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }
            LinearProgressIndicator(
                progress = { power.battery.percent },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.2f),
            )
            val lowThreshold = state.batteryLowAlert?.rule?.threshold
            val highThreshold = state.batteryHighAlert?.rule?.threshold
            if (lowThreshold != null || highThreshold != null) {
                ThresholdMarkers(lowThreshold = lowThreshold, highThreshold = highThreshold)
            }
            PowerTileSecondaryText(power)
        }
    }
}

@Composable
private fun powerTileStatusText(power: PowerInfo): String = when (power.status) {
    Status.FULL -> stringResource(PowerR.string.module_power_battery_status_full)
    Status.CHARGING -> when (power.chargeIO.speed) {
        ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_charging_slow)
        ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_charging_fast)
        else -> stringResource(PowerR.string.module_power_battery_status_charging)
    }
    Status.DISCHARGING -> when (power.chargeIO.speed) {
        ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_discharging_slow)
        ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_discharging_fast)
        else -> stringResource(PowerR.string.module_power_battery_status_discharging)
    }
    else -> stringResource(PowerR.string.module_power_battery_status_unknown)
}

@Composable
private fun PowerTileSecondaryText(power: PowerInfo) {
    val context = LocalContext.current
    val temperatureUnit = LocalPowerTemperatureUnit.current
    val estimationFormatter = remember { PowerEstimationFormatter(context) }
    val temperatureFormatter = remember(context, temperatureUnit) {
        TemperatureFormatter(context, temperatureUnit)
    }
    val estimationText = estimationFormatter.format(power)
    val temperatureText = power.battery.temp?.let { temperatureFormatter.formatTemperature(it) }
    Text(
        text = if (temperatureText != null) "$temperatureText - $estimationText" else estimationText,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun ThresholdMarkers(
    lowThreshold: Float?,
    highThreshold: Float?,
) {
    data class Marker(val threshold: Float, val anchorAtStart: Boolean)

    val markers = listOfNotNull(
        lowThreshold?.let { Marker(it, anchorAtStart = true) },
        highThreshold?.let { Marker(it, anchorAtStart = false) },
    )
    Layout(
        content = {
            lowThreshold?.let {
                MarkerLabel(
                    percentage = (it * 100).toInt(),
                    icon = Icons.TwoTone.Battery1Bar,
                    anchorAtStart = true,
                )
            }
            highThreshold?.let {
                MarkerLabel(
                    percentage = (it * 100).toInt(),
                    icon = Icons.TwoTone.BatteryFull,
                    anchorAtStart = false,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                val marker = markers[index]
                val thresholdX = constraints.maxWidth * marker.threshold
                val x = if (marker.anchorAtStart) {
                    thresholdX.toInt()
                } else {
                    (thresholdX - placeable.width).toInt()
                }.coerceIn(0, constraints.maxWidth - placeable.width)
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@Composable
private fun MarkerLabel(
    percentage: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    anchorAtStart: Boolean,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!anchorAtStart) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(9.dp),
                tint = color,
            )
            Text(
                text = "${percentage}%",
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = color,
            )
        }
        Text(
            text = "▲",
            fontSize = 7.sp,
            lineHeight = 9.sp,
            color = color,
        )
        if (anchorAtStart) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(9.dp),
                tint = color,
            )
            Text(
                text = "${percentage}%",
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = color,
            )
        }
    }
}

@Preview2
@Composable
private fun PowerModuleTileWidePreview() = PreviewWrapper {
    PowerModuleTile(
        state = PowerDashState(
            info = PowerInfo(
                status = Status.DISCHARGING,
                battery = PowerInfo.Battery(level = 72, scale = 100, health = 2, temp = 28.5f),
                chargeIO = ChargeIO(null, null, null, null, null),
            ),
            batteryLowAlert = null,
            showSettings = true,
        ),
        isWide = true,
        onDetailClicked = {},
        onSettingsClicked = {},
    )
}

@Preview2
@Composable
private fun PowerModuleTileHalfPreview() = PreviewWrapper {
    PowerModuleTile(
        state = PowerDashState(
            info = PowerInfo(
                status = Status.DISCHARGING,
                battery = PowerInfo.Battery(level = 12, scale = 100, health = 2, temp = 31f),
                chargeIO = ChargeIO(null, null, null, null, null),
            ),
            batteryLowAlert = null,
            showSettings = false,
        ),
        isWide = false,
        onDetailClicked = {},
        onSettingsClicked = {},
    )
}

@Preview2
@Composable
private fun PowerModuleTileWithThresholdsPreview() = PreviewWrapper {
    PowerModuleTile(
        state = PowerDashState(
            info = PowerInfo(
                status = Status.DISCHARGING,
                battery = PowerInfo.Battery(level = 55, scale = 100, health = 2, temp = 30f),
                chargeIO = ChargeIO(null, null, null, null, null),
            ),
            batteryLowAlert = PowerAlert(
                rule = BatteryLowAlertRule(deviceId = DeviceId("preview"), threshold = 0.20f),
                event = null,
            ),
            batteryHighAlert = PowerAlert(
                rule = BatteryHighAlertRule(deviceId = DeviceId("preview"), threshold = 0.80f),
                event = null,
            ),
            showSettings = true,
        ),
        isWide = true,
        onDetailClicked = {},
        onSettingsClicked = {},
    )
}
