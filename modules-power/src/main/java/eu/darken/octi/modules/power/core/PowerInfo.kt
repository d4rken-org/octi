@file:UseSerializers(InstantSerializer::class)

package eu.darken.octi.modules.power.core

import android.os.BatteryManager
import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class PowerInfo(
    @SerialName("status") val status: Status,
    @SerialName("battery") val battery: Battery,
    @SerialName("chargeIO") val chargeIO: ChargeIO,
) {
    val isCharging: Boolean
        get() = setOf(Status.FULL, Status.CHARGING).contains(status)

    @Serializable
    data class ChargeIO(
        @SerialName("currentNow") val currentNow: Int?,
        @SerialName("currentAvg") val currenAvg: Int?,
        @SerialName("fullSince") val fullSince: Instant?,
        @SerialName("fullAt") val fullAt: Instant?,
        @SerialName("emptyAt") val emptyAt: Instant?,
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

        @Serializable
        enum class Speed {
            @SerialName("SLOW") SLOW,
            @SerialName("NORMAL") NORMAL,
            @SerialName("FAST") FAST,
            ;
        }
    }

    @Serializable
    data class Battery(
        @SerialName("level") val level: Int,
        @SerialName("scale") val scale: Int,
        @SerialName("health") val health: Int?,
        @SerialName("temp") val temp: Float?,
    ) {

        val percent: Float
            get() = level / scale.toFloat()
    }

    @Serializable
    enum class Status(val value: Int) {
        @SerialName("FULL") FULL(BatteryManager.BATTERY_STATUS_FULL),
        @SerialName("CHARGING") CHARGING(BatteryManager.BATTERY_STATUS_CHARGING),
        @SerialName("DISCHARGING") DISCHARGING(BatteryManager.BATTERY_STATUS_DISCHARGING),
        @SerialName("UNKNOWN") UNKNOWN(BatteryManager.BATTERY_STATUS_UNKNOWN),
    }

    sealed class PowerEvent {
        object PowerConnected : PowerEvent()
        object PowerDisconnected : PowerEvent()
    }
}
