package eu.darken.octi.modules.power.ui

import androidx.annotation.DrawableRes
import eu.darken.octi.R

@get:DrawableRes
val eu.darken.octi.modules.power.core.PowerInfo.batteryIconRes: Int
    get() = when {
        isCharging -> R.drawable.ic_baseline_battery_charging_full_24
        status == eu.darken.octi.modules.power.core.PowerInfo.Status.FULL -> R.drawable.ic_baseline_battery_full_24
        battery.percent > 0.85f -> R.drawable.ic_baseline_battery_6_bar_24
        battery.percent > 0.71f -> R.drawable.ic_baseline_battery_5_bar_24
        battery.percent > 0.57f -> R.drawable.ic_baseline_battery_4_bar_24
        battery.percent > 0.42f -> R.drawable.ic_baseline_battery_3_bar_24
        battery.percent > 0.28f -> R.drawable.ic_baseline_battery_2_bar_24
        battery.percent > 0.14f -> R.drawable.ic_baseline_battery_1_bar_24
        battery.percent >= 0.0f -> R.drawable.ic_baseline_battery_0_bar_24
        else -> R.drawable.ic_baseline_battery_unknown_24
    }