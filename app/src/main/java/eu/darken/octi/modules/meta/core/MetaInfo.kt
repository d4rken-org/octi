package eu.darken.octi.modules.meta.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class MetaInfo(
    @Json(name = "deviceLabel") val deviceLabel: String?,

    @Json(name = "octiVersionName") val octiVersionName: String,
    @Json(name = "octiGitSha") val octiGitSha: String,

    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "deviceType") val deviceType: DeviceType,
    @Json(name = "deviceBootedAt") val deviceBootedAt: Instant,

    @Json(name = "androidVersionName") val androidVersionName: String,
    @Json(name = "androidApiLevel") val androidApiLevel: Int,
) {

    @JsonClass(generateAdapter = false)
    enum class DeviceType {
        @Json(name = "phone") PHONE,
    }
}