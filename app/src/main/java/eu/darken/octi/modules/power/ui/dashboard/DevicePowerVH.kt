package eu.darken.octi.modules.power.ui.dashboard

import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.widget.ImageViewCompat
import eu.darken.octi.R
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.common.isBold
import eu.darken.octi.databinding.DashboardDevicePowerItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.core.alerts.BatteryLowAlert
import eu.darken.octi.modules.power.ui.batteryIconRes
import java.time.Duration
import java.time.Instant


class DevicePowerVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<DevicePowerVH.Item, DashboardDevicePowerItemBinding>(
        R.layout.dashboard_device_power_item,
        parent
    ) {

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
                    ColorStateList.valueOf(context.getColorForAttr(R.attr.colorControlNormal))
                )
            }
        }

        powerPrimary.apply {
            val percentTxt = (powerInfo.battery.percent * 100).toInt()
            val stateTxt = when (powerInfo.status) {
                Status.FULL -> {
                    getString(R.string.module_power_battery_status_full)
                }

                Status.CHARGING -> when (powerInfo.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> getString(R.string.module_power_battery_status_charging_slow)
                    ChargeIO.Speed.FAST -> getString(R.string.module_power_battery_status_charging_fast)
                    else -> getString(R.string.module_power_battery_status_charging)
                }

                Status.DISCHARGING -> when (powerInfo.chargeIO.speed) {
                    ChargeIO.Speed.SLOW -> getString(R.string.module_power_battery_status_discharging_slow)
                    ChargeIO.Speed.FAST -> getString(R.string.module_power_battery_status_discharging_fast)
                    else -> getString(R.string.module_power_battery_status_discharging)
                }

                else -> getString(R.string.module_power_battery_status_unknown)
            }
            text = "$percentTxt% • $stateTxt"

            val lowAlert = item.batteryLowAlert
            if (lowAlert?.triggeredAt != null && lowAlert.dismissedAt == null) {
                setTextColor(context.getColor(R.color.error))
                isBold = true
            } else {
                setTextColor(context.getColorForAttr(R.attr.colorOnSurface))
                isBold = false
            }
        }

        powerSecondary.apply {
            val estimationText = when {
                powerInfo.status == Status.FULL && powerInfo.chargeIO.fullSince != null -> {
                    getString(
                        R.string.module_power_battery_full_since_x,
                        DateUtils.getRelativeTimeSpanString(powerInfo.chargeIO.fullSince!!.toEpochMilli())
                    )
                }

                powerInfo.status == Status.CHARGING
                        && powerInfo.chargeIO.fullAt != null
                        && Duration.between(Instant.now(), powerInfo.chargeIO.fullAt).isNegative -> {
                    getString(
                        R.string.module_power_battery_full_since_x,
                        DateUtils.getRelativeTimeSpanString(powerInfo.chargeIO.fullAt!!.toEpochMilli())
                    )
                }

                powerInfo.status == Status.CHARGING && powerInfo.chargeIO.fullAt != null -> {
                    getString(
                        R.string.module_power_battery_full_in_x,
                        DateUtils.getRelativeTimeSpanString(
                            powerInfo.chargeIO.fullAt!!.toEpochMilli(),
                            Instant.now().toEpochMilli(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                    )
                }

                powerInfo.status == Status.DISCHARGING && powerInfo.chargeIO.emptyAt != null -> {
                    getString(
                        R.string.module_power_battery_empty_in_x,
                        DateUtils.getRelativeTimeSpanString(
                            powerInfo.chargeIO.emptyAt!!.toEpochMilli(),
                            Instant.now().toEpochMilli(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                    )
                }

                else -> getString(R.string.module_power_battery_estimation_na)
            }

            val temperatureText = powerInfo.battery.temp?.let { "$it C°" }

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
    }

    data class Item(
        val data: ModuleData<PowerInfo>,
        val batteryLowAlert: BatteryLowAlert?,
        val onSettingsAction: (() -> Unit)?,
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.moduleId.hashCode().toLong()
    }

}