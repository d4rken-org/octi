package eu.darken.octi.meta.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetaInfo(
    @Json(name = "octiVersionName") val octiVersionName: String,
    @Json(name = "octiGitSha") val octiGitSha: String,

    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "deviceType") val deviceType: DeviceType,
    @Json(name = "deviceUptime") val deviceUptime: Long,

    @Json(name = "androidVersionName") val androidVersionName: String,
    @Json(name = "androidApiLevel") val androidApiLevel: Int,
) {

    @JsonClass(generateAdapter = false)
    enum class DeviceType {
        @Json(name = "phone") PHONE,
    }
}