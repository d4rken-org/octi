package eu.darken.octi.modules.power.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.NotificationsNone
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter
import eu.darken.octi.modules.power.ui.batteryIcon

data class PowerDashState(
    val info: PowerInfo,
    val batteryLowAlert: PowerAlert<BatteryLowAlertRule>?,
    val showSettings: Boolean,
)

@Composable
fun PowerModuleItem(
    state: PowerDashState,
    onDetailClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    val power = state.info
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = power.batteryIcon,
            contentDescription = null,
            tint = if (power.battery.percent < 0.1f && !power.isCharging) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val percentTxt = (power.battery.percent * 100).toInt()
            val stateTxt = powerStatusText(power)
            val lowAlert = state.batteryLowAlert
            val isAlertActive = lowAlert?.triggeredAt != null && lowAlert.dismissedAt == null
            Text(
                text = "$percentTxt% - $stateTxt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isAlertActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isAlertActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            PowerSecondaryText(power)
        }
        if (state.batteryLowAlert != null) {
            Icon(
                imageVector = Icons.TwoTone.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        if (state.showSettings) {
            IconButton(onClick = onSettingsClicked, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.TwoTone.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun powerStatusText(power: PowerInfo): String = when (power.status) {
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
private fun PowerSecondaryText(power: PowerInfo) {
    val context = LocalContext.current
    val estimationFormatter = remember { PowerEstimationFormatter(context) }
    val temperatureFormatter = remember { TemperatureFormatter(context) }
    val estimationText = estimationFormatter.format(power)
    val temperatureText = power.battery.temp?.let { temperatureFormatter.formatTemperature(it) }
    Text(
        text = if (temperatureText != null) "$temperatureText - $estimationText" else estimationText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Preview2
@Composable
private fun PowerModuleItemPreview() = PreviewWrapper {
    PowerModuleItem(
        state = PowerDashState(
            info = PowerInfo(
                status = Status.DISCHARGING,
                battery = PowerInfo.Battery(level = 75, scale = 100, health = 2, temp = 28.5f),
                chargeIO = ChargeIO(null, null, null, null, null),
            ),
            batteryLowAlert = null,
            showSettings = true,
        ),
        onDetailClicked = {},
        onSettingsClicked = {},
    )
}
