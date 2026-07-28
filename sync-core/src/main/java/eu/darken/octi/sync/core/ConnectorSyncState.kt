package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Tracks the last-sent payload hash and its age per (connectorId, moduleId) pair.
 * In-memory only — records repopulate naturally on restart because writeFLow
 * fires, payloads get cached, and first sync sends everything (no records = all mismatches).
 */
@Singleton
class ConnectorSyncState @Inject constructor() {

    private data class Record(val hash: String, val sentAt: TimeMark)

    private val records = ConcurrentHashMap<Pair<ConnectorId, ModuleId>, Record>()

    /**
     * Monotonic on purpose: a wall-clock jump must not make a record look eternally fresh (never
     * refreshing a static payload) or instantly expired. Overridable for tests only.
     */
    internal var timeSource: TimeSource = TimeSource.Monotonic

    /** Hash and age of the last successful write, captured together. */
    data class SentRecord(val hash: String, val age: Duration)

    /**
     * One atomic snapshot: hash and age come from the same record version. Reading them through
     * separate calls could observe two different writes and mix a stale hash with a fresh age.
     */
    fun getRecord(connectorId: ConnectorId, moduleId: ModuleId): SentRecord? =
        records[connectorId to moduleId]?.let { SentRecord(hash = it.hash, age = it.sentAt.elapsedNow()) }

    fun setHash(connectorId: ConnectorId, moduleId: ModuleId, hash: String) {
        records[connectorId to moduleId] = Record(hash = hash, sentAt = timeSource.markNow())
    }

    fun clearConnector(connectorId: ConnectorId) {
        records.keys.removeAll { it.first == connectorId }
    }
}
