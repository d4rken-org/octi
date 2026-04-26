package eu.darken.octi.sync.core.blob

import kotlin.time.Instant

/**
 * Per-(connectorId, blobKey) state of an upload that has either failed at least once or been
 * pre-flight rejected. Surfaced as a flow from [BlobManager.retryStatus] so the UI can render
 * "retrying in 28 m" / "stopped" / "too large" / "out of space" chips per file row.
 *
 * Absence of an entry in the flow means [Ok] — no failures recorded.
 */
sealed interface RetryStatus {
    /** Nominal: no recorded failures or backoff for this (connector, blob). */
    data object Ok : RetryStatus

    /** Transient failure; will retry automatically at [nextAttemptAt]. */
    data class RetryingAt(val nextAttemptAt: Instant, val failureCount: Int) : RetryStatus

    /**
     * Backoff schedule exhausted — the maintenance loop will not retry until backoff is
     * cleared (via pause/resume of the connector, or a user-initiated retry).
     */
    data object Stopped : RetryStatus

    /**
     * Pre-flight rejection: the file exceeds the connector's per-file cap. Cannot resolve via
     * retry — the file would have to be re-shared at a smaller size.
     */
    data class FileTooLarge(val maxBytes: Long) : RetryStatus

    /**
     * Pre-flight rejection: the connector's account quota is full. May resolve once the user
     * frees space; user-tapped Retry re-checks.
     */
    data class QuotaExceeded(val usedBytes: Long, val totalBytes: Long) : RetryStatus

    /**
     * Pre-flight rejection: the server's underlying disk is below the operator's configured
     * threshold. Out of the user's control — they can only wait or contact the operator.
     * User-tapped Retry re-checks once the server recovers.
     */
    data object ServerStorageLow : RetryStatus
}
