package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import okio.ByteString
import java.time.Instant

interface Sync {
    interface Hub {
        val connectors: Flow<Collection<Connector>>
    }

    interface Connector {

        val state: Flow<State>

        /**
         * Attempt to read and write data
         */
        suspend fun sync()

        /**
         * Data that is written on the next **sync**
         */
        suspend fun write(toWrite: Write)

        interface State {
            val isSyncing: Boolean
            val syncedAt: Instant?
            val data: Read?
        }

    }

    /**
     * Data read from a connector
     */
    interface Read {
        val devices: Collection<Device>

        interface Device {
            val deviceId: SyncDeviceId
            val modules: Collection<Module>
        }

        interface Module {
            val moduleId: SyncModuleId
            val createdAt: Instant
            val modifiedAt: Instant
            val payload: ByteArray
        }
    }

    /**
     * Data written to a connector
     */
    interface Write {
        val deviceId: SyncDeviceId
        val modules: Collection<Module>

        interface Module {
            val moduleId: SyncModuleId
            val payload: ByteString
        }
    }

}