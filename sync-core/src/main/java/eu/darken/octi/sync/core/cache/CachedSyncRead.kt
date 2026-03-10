@file:UseSerializers(InstantSerializer::class, ByteStringSerializer::class)

package eu.darken.octi.sync.core.cache

import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import okio.ByteString
import java.time.Instant

@Serializable
data class CachedSyncRead(
    @SerialName("accountId") override val connectorId: ConnectorId,
    @SerialName("devices") override val devices: Collection<Device>,
) : SyncRead {

    @Serializable
    data class Device(
        @SerialName("deviceId") override val deviceId: DeviceId,
        @SerialName("modules") override val modules: Collection<Module>,
    ) : SyncRead.Device {

        @Serializable
        data class Module(
            @SerialName("accountId") override val connectorId: ConnectorId,
            @SerialName("deviceId") override val deviceId: DeviceId,
            @SerialName("moduleId") override val moduleId: ModuleId,
            @SerialName("modifiedAt") override val modifiedAt: Instant,
            @SerialName("payload") override val payload: ByteString,
        ) : SyncRead.Device.Module
    }
}
