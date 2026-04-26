package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.ConnectorId
import kotlin.time.Instant

/**
 * Live storage status for a single connector. Pre-flight, sync-list, and file-share UI all project
 * from this single source — there is no separate per-feature quota fetch path.
 *
 * The four cases distinguish what `null` used to collapse:
 * - [Loading] — a refresh is in flight; render last-known if any.
 * - [Ready] — fresh snapshot available.
 * - [Unavailable] — last fetch failed (transient or terminal); render last-known if any.
 * - [Unsupported] — the backend does not expose storage info (e.g. legacy unlimited Drive); UI hides the row, pre-flight skips quota check.
 */
sealed interface StorageStatus {
    val connectorId: ConnectorId
    val lastKnown: StorageSnapshot?

    data class Loading(
        override val connectorId: ConnectorId,
        override val lastKnown: StorageSnapshot?,
    ) : StorageStatus

    data class Ready(
        override val connectorId: ConnectorId,
        val snapshot: StorageSnapshot,
    ) : StorageStatus {
        override val lastKnown: StorageSnapshot get() = snapshot
    }

    data class Unavailable(
        override val connectorId: ConnectorId,
        val error: Throwable,
        override val lastKnown: StorageSnapshot?,
    ) : StorageStatus

    data class Unsupported(
        override val connectorId: ConnectorId,
    ) : StorageStatus {
        override val lastKnown: StorageSnapshot? get() = null
    }
}

/**
 * Numeric storage snapshot. Authoritative for pre-flight (always via [availableBytes] +
 * [requiredStoredBytes]). [perFileOverheadBytes] is internal to pre-flight; UI uses
 * [requiredStoredBytes] so encryption framing details never leak.
 */
data class StorageSnapshot(
    val connectorId: ConnectorId,
    /** Human-readable account identifier, when one is available. null = not displayable. */
    val accountLabel: String?,
    val usedBytes: Long,
    val totalBytes: Long,
    /**
     * Server-canonical free-space figure. For OctiServer this is `accountQuotaBytes - usedBytes - reservedBytes`
     * (active upload sessions count against your quota). For GDrive this is `limit - usage`. Always
     * the right number for "can I share another file."
     */
    val availableBytes: Long,
    /** Per-file size cap (already net of [perFileOverheadBytes]). null = no per-file cap. */
    val maxFileBytes: Long?,
    /**
     * Per-file overhead bytes added by the backend on top of plaintext (Tink AEAD framing for
     * OctiServer; 0 for GDrive). Pre-flight uses this via [requiredStoredBytes]; UI doesn't need it.
     */
    val perFileOverheadBytes: Long,
    val updatedAt: Instant,
) {
    /** Plaintext bytes -> total bytes that will be consumed on the backend. */
    fun requiredStoredBytes(plaintextBytes: Long): Long = plaintextBytes + perFileOverheadBytes
}
