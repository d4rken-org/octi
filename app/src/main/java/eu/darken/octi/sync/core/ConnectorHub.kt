package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow

interface ConnectorHub {
    val connectors: Flow<Collection<SyncConnector>>
}