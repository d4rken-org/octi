package eu.darken.octi.sync.core

import okio.ByteString
import java.time.Instant
import java.util.*

/**
 * Data read from a connector
 */
interface SyncRead {
    val readId: UUID
    val devices: Collection<Device>

    interface Device {
        val deviceId: DeviceId
        val modules: Collection<Module>

        interface Module {
            val moduleId: ModuleId
            val createdAt: Instant
            val modifiedAt: Instant
            val payload: ByteString
        }
    }
}