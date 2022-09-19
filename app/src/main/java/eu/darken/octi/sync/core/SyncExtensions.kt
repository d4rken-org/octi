package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


inline fun <reified T : SyncConnector> SyncManager.getConnectorById(identifier: SyncConnector.Identifier): Flow<T> {
    return connectors.map { connecs -> connecs.single { it.identifier == identifier } }.map { it as T }
}