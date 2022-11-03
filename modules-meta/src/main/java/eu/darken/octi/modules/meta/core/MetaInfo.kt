package eu.darken.octi.modules.meta.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.sync.core.DeviceId
import java.time.Instant

@JsonClass(generateAdapter = true)
data class MetaInfo(
    @Json(name = "deviceLabel") val deviceLabel: String?,

    @Json(name = "deviceId") val deviceId: DeviceId,

    @Json(name = "octiVersionName") val octiVersionName: String,
    @Json(name = "octiGitSha") val octiGitSha: String,

    @Json(name = "deviceManufacturer") val deviceManufacturer: String,
    @Json(name = "deviceName") val deviceName: String,
    @Json(name = "deviceType") val deviceType: DeviceType,
    @Json(name = "deviceBootedAt") val deviceBootedAt: Instant,

    @Json(name = "androidVersionName") val androidVersionName: String,
    @Json(name = "androidApiLevel") val androidApiLevel: Int,
    @Json(name = "androidSecurityPatch") val androidSecurityPatch: String?,
) {

    val labelOrFallback: String
        get() = deviceLabel ?: deviceName

    @JsonClass(generateAdapter = false)
    enum class DeviceType {
        @Json(name = "PHONE") PHONE,
        @Json(name = "TABLET") TABLET,
    }
}