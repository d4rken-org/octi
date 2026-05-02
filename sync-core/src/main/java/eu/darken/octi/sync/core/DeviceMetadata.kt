package eu.darken.octi.sync.core

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class DeviceMetadata(
    @SerialName("deviceId")
    val deviceId: DeviceId,
    @SerialName("version")
    val version: String? = null,
    @SerialName("platform")
    val platform: String? = null,
    @SerialName("label")
    val label: String? = null,
    @Serializable(with = InstantSerializer::class)
    @SerialName("lastSeen")
    val lastSeen: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    @SerialName("addedAt")
    val addedAt: Instant? = null,
)

val SyncConnectorState.knownDevices: Set<DeviceId>
    get() = deviceMetadata.map { it.deviceId }.toSet()
