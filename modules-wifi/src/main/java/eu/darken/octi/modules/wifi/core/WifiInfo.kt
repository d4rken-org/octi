package eu.darken.octi.modules.wifi.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WifiInfo(
    @SerialName("currentWifi") val currentWifi: Wifi?,
) {

    @Serializable
    data class Wifi(
        @SerialName("ssid") val ssid: String?,
        @SerialName("reception") val reception: Float?,
        @SerialName("freqType") val freqType: Type?,
    ) {

        @Serializable
        enum class Type {
            @SerialName("UNKNOWN") UNKNOWN,
            @SerialName("5GHZ") FIVE_GHZ,
            @SerialName("2.4GHZ") TWO_POINT_FOUR_GHZ,
            ;
        }
    }
}
