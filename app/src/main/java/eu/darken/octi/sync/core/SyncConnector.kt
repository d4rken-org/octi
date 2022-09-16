package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SyncConnector {

    val state: Flow<State>
    val data: Flow<SyncRead?>

    /**
     * Sync all modules
     */
    suspend fun read()

    /**
     * Data that is written on the next **sync**
     */
    suspend fun write(toWrite: SyncWrite)

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