package eu.darken.octi.battery.core

import android.content.Intent
import android.os.BatteryManager

data class BatteryInfo(
    val batteryLevel: Int,
    val batteryScale: Int,
    val status: Status,
) {
    enum class Status(val value: Int) {
        FULL(BatteryManager.BATTERY_STATUS_FULL),
        CHARGING(BatteryManager.BATTERY_STATUS_CHARGING),
        DISCHARGING(BatteryManager.BATTERY_STATUS_DISCHARGING),
        UNKNOWN(BatteryManager.BATTERY_STATUS_UNKNOWN),
    }

    companion object {
        fun fromIntent(intent: Intent) = with(intent) {
            BatteryInfo(
                batteryLevel = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                batteryScale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                status = getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { statusValue ->
                    Status.values().firstOrNull { it.value == statusValue } ?: Status.UNKNOWN
                },
            )
        }
    }
}