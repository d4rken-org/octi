package eu.darken.octi.sync.core.cache

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CachedConnectorDeviceMetadata(
    @SerialName("schemaVersion") val schemaVersion: Int = SCHEMA_VERSION,
    @SerialName("connectorId") val connectorId: ConnectorId,
    @Serializable(with = InstantSerializer::class) @SerialName("cachedAt") val cachedAt: Instant,
    @SerialName("devices") val devices: List<DeviceMetadata> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}
