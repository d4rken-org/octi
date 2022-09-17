package eu.darken.octi.power.core

import android.content.Intent
import android.os.BatteryManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PowerInfo(
    @Json(name = "battery") val battery: Battery,
) {
    val isCharging: Boolean
        get() = setOf(Battery.Status.FULL, Battery.Status.CHARGING).contains(battery.status)

    val batteryPercent: Float
        get() = battery.batteryLevel / battery.batteryScale.toFloat()

    sealed class PowerEvent {
        object PowerConnected : PowerEvent()
        object PowerDisconnected : PowerEvent()
    }

    @JsonClass(generateAdapter = true)
    data class Battery(
        @Json(name = "batteryLevel") val batteryLevel: Int,
        @Json(name = "batteryScale") val batteryScale: Int,
        @Json(name = "status") val status: Status,
    ) {
        @JsonClass(generateAdapter = false)
        enum class Status(val value: Int) {
            @Json(name = "FULL") FULL(BatteryManager.BATTERY_STATUS_FULL),
            @Json(name = "CHARGING") CHARGING(BatteryManager.BATTERY_STATUS_CHARGING),
            @Json(name = "DISCHARGING") DISCHARGING(BatteryManager.BATTERY_STATUS_DISCHARGING),
            @Json(name = "UNKNOWN") UNKNOWN(BatteryManager.BATTERY_STATUS_UNKNOWN),
        }

        companion object {
            fun fromIntent(intent: Intent) = with(intent) {
                Battery(
                    batteryLevel = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                    batteryScale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                    status = getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { statusValue ->
                        Status.values().firstOrNull { it.value == statusValue } ?: Status.UNKNOWN
                    },
                )
            }
        }
    }
}