package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.DeviceId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

@Serializable
data class MetaInfo(
    @SerialName("deviceLabel") val deviceLabel: String?,

    @SerialName("deviceId") val deviceId: DeviceId,

    @SerialName("octiVersionName") val octiVersionName: String,
    @SerialName("octiGitSha") val octiGitSha: String,

    @SerialName("deviceManufacturer") val deviceManufacturer: String,
    @SerialName("deviceName") val deviceName: String,
    @SerialName("deviceType") val deviceType: DeviceType,
    @Serializable(with = InstantSerializer::class) @SerialName("deviceBootedAt") val deviceBootedAt: Instant? = null,

    @SerialName("androidVersionName") val androidVersionName: String? = null,
    @SerialName("androidApiLevel") val androidApiLevel: Int? = null,
    @SerialName("androidSecurityPatch") val androidSecurityPatch: String? = null,

    @SerialName("osType") val osType: String? = null,
    @SerialName("osVersionName") val osVersionName: String? = null,
) {

    val labelOrFallback: String
        get() = deviceLabel ?: deviceName

    @Serializable(with = DeviceTypeSerializer::class)
    enum class DeviceType {
        PHONE,
        TABLET,
        DESKTOP,
        BROWSER,
        UNKNOWN,
    }
}

internal object DeviceTypeSerializer : KSerializer<MetaInfo.DeviceType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("eu.darken.octi.modules.meta.core.MetaInfo.DeviceType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: MetaInfo.DeviceType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): MetaInfo.DeviceType {
        val raw = decoder.decodeString()
        return MetaInfo.DeviceType.entries.firstOrNull { it.name == raw } ?: MetaInfo.DeviceType.UNKNOWN
    }
}
