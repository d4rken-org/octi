package eu.darken.octi.modules.power.ui.dashboard

import android.os.BatteryManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter

@Composable
internal fun PowerDetailSheet(
    info: PowerInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val temperatureFormatter = remember { TemperatureFormatter(context) }
    val estimationFormatter = remember { PowerEstimationFormatter(context) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        PowerDetailContent(info, temperatureFormatter, estimationFormatter)
    }
}

@Composable
private fun PowerDetailContent(
    info: PowerInfo,
    temperatureFormatter: TemperatureFormatter = TemperatureFormatter(LocalContext.current),
    estimationFormatter: PowerEstimationFormatter = PowerEstimationFormatter(LocalContext.current),
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = stringResource(PowerR.string.module_power_label),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        DetailRow(
            label = stringResource(PowerR.string.module_power_detail_level_label),
            value = "${(info.battery.percent * 100).toInt()}%",
        )
        DetailRow(
            label = stringResource(PowerR.string.module_power_detail_status_label),
            value = when (info.status) {
                Status.FULL -> stringResource(PowerR.string.module_power_battery_status_full)
                Status.CHARGING -> stringResource(PowerR.string.module_power_battery_status_charging)
                Status.DISCHARGING -> stringResource(PowerR.string.module_power_battery_status_discharging)
                Status.UNKNOWN -> stringResource(PowerR.string.module_power_battery_status_unknown)
            },
        )
        DetailRow(
            label = stringResource(PowerR.string.module_power_detail_speed_label),
            value = when (info.status) {
                Status.CHARGING -> when (info.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_charging_slow)
                    ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_charging_fast)
                    ChargeIO.Speed.NORMAL -> stringResource(PowerR.string.module_power_battery_status_charging)
                }

                Status.DISCHARGING -> when (info.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_discharging_slow)
                    ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_discharging_fast)
                    ChargeIO.Speed.NORMAL -> stringResource(PowerR.string.module_power_battery_status_discharging)
                }

                else -> stringResource(CommonR.string.general_na_label)
            },
        )
        DetailRow(
            label = stringResource(PowerR.string.module_power_detail_temperature_label),
            value = info.battery.temp?.let { temperatureFormatter.formatTemperature(it) }
                ?: stringResource(CommonR.string.general_na_label),
        )
        DetailRow(
            label = stringResource(PowerR.string.module_power_detail_estimation_label),
            value = estimationFormatter.format(info),
        )
        DetailRow(
            label = stringResource(PowerR.string.module_power_detail_health_label),
            value = when (info.battery.health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> stringResource(PowerR.string.module_power_detail_health_good)
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> stringResource(PowerR.string.module_power_detail_health_overheat)
                BatteryManager.BATTERY_HEALTH_DEAD -> stringResource(PowerR.string.module_power_detail_health_dead)
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> stringResource(PowerR.string.module_power_detail_health_over_voltage)
                BatteryManager.BATTERY_HEALTH_COLD -> stringResource(PowerR.string.module_power_detail_health_cold)
                else -> stringResource(PowerR.string.module_power_detail_health_unknown)
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview2
@Composable
private fun PowerDetailContentPreview() = PreviewWrapper {
    PowerDetailContent(
        info = PowerInfo(
            status = Status.CHARGING,
            battery = PowerInfo.Battery(level = 82, scale = 100, health = 2, temp = 31.2f),
            chargeIO = ChargeIO(null, null, null, null, null),
        ),
    )
}
