package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import okio.ByteString
import java.time.Instant
import java.util.*

interface Sync {
    interface Hub {
        val connectors: Flow<Collection<Connector>>
    }

    interface Connector {

        val state: Flow<State>
        val data: Flow<Read?>

        /**
         * Sync all modules
         */
        suspend fun read()

        /**
         * Data that is written on the next **sync**
         */
        suspend fun write(toWrite: Write)

        interface State {
            val isReading: Boolean
            val lastReadAt: Instant?

            val isWriting: Boolean
            val lastWriteAt: Instant?

            val lastError: Exception?

            val stats: Stats?

            val isBusy: Boolean
                get() = isReading || isWriting

            val lastSyncAt: Instant?
                get() = setOf(lastReadAt, lastWriteAt).filterNotNull().maxByOrNull { it }

            data class Stats(
                val timestamp: Instant,
                val storageUsed: Long,
                val storageTotal: Long,
            ) {
                val storageFree: Long
                    get() = storageTotal - storageUsed
            }
        }
    }

    /**
     * Data read from a connector
     */
    interface Read {
        val readId: UUID
        val devices: Collection<Device>

        interface Device {
            val deviceId: DeviceId
            val modules: Collection<Module>
        }

        interface Module {
            val moduleId: ModuleId
            val createdAt: Instant
            val modifiedAt: Instant
            val payload: ByteString
        }
    }

    /**
     * Data written to a connector
     */
    interface Write {
        val writeId: UUID
        val deviceId: DeviceId
        val modules: Collection<Module>

        interface Module {
            val moduleId: ModuleId
            val payload: ByteString
        }
    }
}