package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface SyncConnector {

    val identifier: ConnectorId

    val state: Flow<SyncConnectorState>
    val data: Flow<SyncRead?>

    val syncEvents: Flow<SyncEvent> get() = emptyFlow()

    /**
     * Data that is written on the next **sync**
     */
    suspend fun write(toWrite: SyncWrite)

    /**
     * Wipe all Octi data stored via this connector
     */
    suspend fun resetData()

    suspend fun deleteDevice(deviceId: DeviceId)

    suspend fun sync(options: SyncOptions)
}