package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow

interface SyncConnector {

    val identifier: ConnectorId

    val state: Flow<SyncConnectorState>
    val data: Flow<SyncRead?>

    /**
     * Data that is written on the next **sync**
     */
    suspend fun write(toWrite: SyncWrite)

    /**
     * Wipe all Octi data stored via this connector
     */
    suspend fun deleteAll()

    suspend fun deleteDevice(deviceId: DeviceId)

    suspend fun sync(options: SyncOptions)
}