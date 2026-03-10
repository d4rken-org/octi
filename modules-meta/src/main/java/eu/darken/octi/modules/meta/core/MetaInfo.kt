@file:UseSerializers(InstantSerializer::class)

package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.DeviceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

@Serializable
data class MetaInfo(
    @SerialName("deviceLabel") val deviceLabel: String?,

    @SerialName("deviceId") val deviceId: DeviceId,

    @SerialName("octiVersionName") val octiVersionName: String,
    @SerialName("octiGitSha") val octiGitSha: String,

    @SerialName("deviceManufacturer") val deviceManufacturer: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("deviceType") val deviceType: DeviceType,
    @SerialName("deviceBootedAt") val deviceBootedAt: Instant,

    @SerialName("androidVersionName") val androidVersionName: String,
    @SerialName("androidApiLevel") val androidApiLevel: Int,
    @SerialName("androidSecurityPatch") val androidSecurityPatch: String?,
) {

    val labelOrFallback: String
        get() = deviceLabel ?: deviceName

    @Serializable
    enum class DeviceType {
        @SerialName("PHONE") PHONE,
        @SerialName("TABLET") TABLET,
        @SerialName("UNKNOWN") UNKNOWN,
    }
}
