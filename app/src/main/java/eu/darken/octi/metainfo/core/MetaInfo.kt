package eu.darken.octi.metainfo.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetaInfo(
    @Json(name = "versionName") val versionName: String,
    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "deviceType") val deviceType: DeviceType,
    @Json(name = "androidVersionName") val androidVersionName: String,
    @Json(name = "androidApiLevel") val androidApiLevel: Int,
) {

    @JsonClass(generateAdapter = false)
    enum class DeviceType {
        @Json(name = "phone") PHONE,
    }
}