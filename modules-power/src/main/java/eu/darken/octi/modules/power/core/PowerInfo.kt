package eu.darken.octi.modules.power.core

import android.os.BatteryManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class PowerInfo(
    @Json(name = "status") val status: Status,
    @Json(name = "battery") val battery: Battery,
    @Json(name = "chargeIO") val chargeIO: ChargeIO,
) {
    val isCharging: Boolean
        get() = setOf(Status.FULL, Status.CHARGING).contains(status)

    @JsonClass(generateAdapter = true)
    data class ChargeIO(
        @Json(name = "currentNow") val currentNow: Int?,
        @Json(name = "currentAvg") val currenAvg: Int?,
        @Json(name = "fullSince") val fullSince: Instant?,
        @Json(name = "fullAt") val fullAt: Instant?,
        @Json(name = "emptyAt") val emptyAt: Instant?,
    ) {
        val speed: Speed
            get() = when {
                currentNow == null -> Speed.NORMAL
                currentNow > 0 -> {
                    when {
                        currentNow > (2.5 * 1000000) -> Speed.FAST
                        currentNow > (1.0 * 1000000) -> Speed.NORMAL
                        else -> Speed.SLOW
                    }
                }
                else -> Speed.NORMAL
            }

        @JsonClass(generateAdapter = false)
        enum class Speed {
            @Json(name = "SLOW") SLOW,
            @Json(name = "NORMAL") NORMAL,
            @Json(name = "FAST") FAST,
            ;
        }
    }

    @JsonClass(generateAdapter = true)
    data class Battery(
        @Json(name = "level") val level: Int,
        @Json(name = "scale") val scale: Int,
        @Json(name = "health") val health: Int?,
        @Json(name = "temp") val temp: Float?,
    ) {

        val percent: Float
            get() = level / scale.toFloat()
    }

    @JsonClass(generateAdapter = false)
    enum class Status(val value: Int) {
        @Json(name = "FULL") FULL(BatteryManager.BATTERY_STATUS_FULL),
        @Json(name = "CHARGING") CHARGING(BatteryManager.BATTERY_STATUS_CHARGING),
        @Json(name = "DISCHARGING") DISCHARGING(BatteryManager.BATTERY_STATUS_DISCHARGING),
        @Json(name = "UNKNOWN") UNKNOWN(BatteryManager.BATTERY_STATUS_UNKNOWN),
    }

    sealed class PowerEvent {
        object PowerConnected : PowerEvent()
        object PowerDisconnected : PowerEvent()
    }
}