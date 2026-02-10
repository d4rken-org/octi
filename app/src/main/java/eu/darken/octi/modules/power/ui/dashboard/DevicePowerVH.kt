package eu.darken.octi.modules.power.ui.dashboard

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.widget.ImageViewCompat
import com.google.android.material.R as MaterialR
import eu.darken.octi.R
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.common.isBold
import eu.darken.octi.databinding.DashboardDevicePowerItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.core.alert.BatteryLowAlertRule
import eu.darken.octi.modules.power.core.alert.PowerAlert
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter
import eu.darken.octi.modules.power.ui.batteryIconRes


class DevicePowerVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<DevicePowerVH.Item, DashboardDevicePowerItemBinding>(
        R.layout.dashboard_device_power_item,
        parent
    ) {

    private val temperatureFormatter = TemperatureFormatter(itemView.context)
    private val estimationFormatter = PowerEstimationFormatter(itemView.context)

    override val viewBinding = lazy { DashboardDevicePowerItemBinding.bind(itemView) }

    override val onBindData: DashboardDevicePowerItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val powerInfo = item.data.data

        powerIcon.apply {
            setImageResource(powerInfo.batteryIconRes)
            if (powerInfo.battery.percent < 0.1f && !powerInfo.isCharging) {
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(context.getColor(R.color.error))
                )
            } else {
                ImageViewCompat.setImageTintList(
                    this,
                    ColorStateList.valueOf(context.getColorForAttr(MaterialR.attr.colorControlNormal))
                )
            }
        }

        powerPrimary.apply {
            val percentTxt = (powerInfo.battery.percent * 100).toInt()
            val stateTxt = when (powerInfo.status) {
                Status.FULL -> {
                    getString(PowerR.string.module_power_battery_status_full)
                }

                Status.CHARGING -> when (powerInfo.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> getString(PowerR.string.module_power_battery_status_charging_slow)
                    ChargeIO.Speed.FAST -> getString(PowerR.string.module_power_battery_status_charging_fast)
                    else -> getString(PowerR.string.module_power_battery_status_charging)
                }

                Status.DISCHARGING -> when (powerInfo.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> getString(PowerR.string.module_power_battery_status_discharging_slow)
                    ChargeIO.Speed.FAST -> getString(PowerR.string.module_power_battery_status_discharging_fast)
                    else -> getString(PowerR.string.module_power_battery_status_discharging)
                }

                else -> getString(PowerR.string.module_power_battery_status_unknown)
            }
            text = "$percentTxt% • $stateTxt"

            val lowAlert = item.batteryLowAlert
            if (lowAlert?.triggeredAt != null && lowAlert.dismissedAt == null) {
                setTextColor(context.getColor(R.color.error))
                isBold = true
            } else {
                setTextColor(context.getColorForAttr(MaterialR.attr.colorOnSurface))
                isBold = false
            }
        }

        powerSecondary.apply {
            val estimationText = estimationFormatter.format(powerInfo)

            val temperatureText = powerInfo.battery.temp?.let {
                temperatureFormatter.formatTemperature(it)
            }

            text = if (temperatureText != null) {
                "$temperatureText - $estimationText"
            } else {
                estimationText
            }
        }

        alertsIcon.isGone = item.batteryLowAlert == null
        settingsAction.apply {
            setOnClickListener { item.onSettingsAction?.invoke() }
            isGone = item.onSettingsAction == null
        }

        itemView.setOnClickListener { item.onDetailClicked() }
    }

    data class Item(
        val data: ModuleData<PowerInfo>,
        val batteryLowAlert: PowerAlert<BatteryLowAlertRule>?,
        val onSettingsAction: (() -> Unit)?,
        val onDetailClicked: () -> Unit,
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.moduleId.hashCode().toLong()
    }

}