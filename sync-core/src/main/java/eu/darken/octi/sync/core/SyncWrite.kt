package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import okio.ByteString
import java.util.*

/**
 * Data written to a connector
 */
interface SyncWrite {
    val writeId: UUID
    val deviceId: DeviceId
    val modules: Collection<Device.Module>

    interface Device {
        interface Module {
            val moduleId: ModuleId
            val payload: ByteString
        }
    }
}