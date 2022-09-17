package eu.darken.octi.sync.core

import okio.ByteString
import java.util.*

/**
 * Data written to a connector
 */
interface SyncWrite {
    val writeId: UUID
    val deviceId: SyncDeviceId
    val modules: Collection<Module>

    interface Module {
        val moduleId: SyncModuleId
        val payload: ByteString
    }
}