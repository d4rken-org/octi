package eu.darken.octi.sync.core.cache

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.modules.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead
import okio.ByteString
import java.time.Instant

@JsonClass(generateAdapter = true)
data class CachedSyncRead(
    @Json(name = "accountId") override val connectorId: ConnectorId,
    @Json(name = "devices") override val devices: Collection<Device>
) : SyncRead {

    @JsonClass(generateAdapter = true)
    data class Device(
        @Json(name = "deviceId") override val deviceId: DeviceId,
        @Json(name = "modules") override val modules: Collection<Module>
    ) : SyncRead.Device {

        @JsonClass(generateAdapter = true)
        data class Module(
            @Json(name = "accountId") override val accountId: ConnectorId,
            @Json(name = "deviceId") override val deviceId: DeviceId,
            @Json(name = "moduleId") override val moduleId: ModuleId,
            @Json(name = "createdAt") override val createdAt: Instant,
            @Json(name = "modifiedAt") override val modifiedAt: Instant,
            @Json(name = "payload") override val payload: ByteString
        ) : SyncRead.Device.Module
    }
}