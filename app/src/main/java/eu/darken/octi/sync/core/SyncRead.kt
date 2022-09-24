package eu.darken.octi.sync.core

import eu.darken.octi.modules.ModuleId
import okio.ByteString
import java.time.Instant

interface SyncRead {
    val connectorId: ConnectorId
    val devices: Collection<Device>

    interface Device {
        val deviceId: DeviceId
        val modules: Collection<Module>

        interface Module {
            val connectorId: ConnectorId
            val deviceId: DeviceId
            val moduleId: ModuleId
            val createdAt: Instant
            val modifiedAt: Instant
            val payload: ByteString
        }
    }
}