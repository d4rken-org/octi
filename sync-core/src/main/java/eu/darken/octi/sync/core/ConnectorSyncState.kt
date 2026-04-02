package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the last-sent payload hash per (connectorId, moduleId) pair.
 * In-memory only — hashes repopulate naturally on restart because writeFLow
 * fires, payloads get cached, and first sync sends everything (no hashes = all mismatches).
 */
@Singleton
class ConnectorSyncState @Inject constructor() {

    private val hashes = ConcurrentHashMap<Pair<ConnectorId, ModuleId>, String>()

    fun getHash(connectorId: ConnectorId, moduleId: ModuleId): String? =
        hashes[connectorId to moduleId]

    fun setHash(connectorId: ConnectorId, moduleId: ModuleId, hash: String) {
        hashes[connectorId to moduleId] = hash
    }

    fun clearConnector(connectorId: ConnectorId) {
        hashes.keys.removeAll { it.first == connectorId }
    }
}
