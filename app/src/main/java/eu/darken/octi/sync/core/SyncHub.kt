package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow

interface SyncHub {
    val connectors: Flow<Collection<SyncConnector>>
}