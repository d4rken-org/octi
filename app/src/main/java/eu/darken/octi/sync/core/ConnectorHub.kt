package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow

interface ConnectorHub {
    val connectors: Flow<Collection<SyncConnector>>

    suspend fun owns(connectorId: ConnectorId): Boolean

    suspend fun remove(connectorId: ConnectorId)
}