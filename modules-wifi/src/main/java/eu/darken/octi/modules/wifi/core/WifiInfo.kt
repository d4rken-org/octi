package eu.darken.octi.modules.wifi.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WifiInfo(
    @Json(name = "currentWifi") val currentWifi: Wifi?
) {

    @JsonClass(generateAdapter = true)
    data class Wifi(
        @Json(name = "ssid") val ssid: String?,
        @Json(name = "reception") val reception: Float?,
        @Json(name = "freqType") val freqType: Type?,
    ) {

        @JsonClass(generateAdapter = false)
        enum class Type {
            @Json(name = "UNKNOWN") UNKNOWN,
            @Json(name = "5GHZ") FIVE_GHZ,
            @Json(name = "2.4GHZ") TWO_POINT_FOUR_GHZ,
            ;
        }
    }
}