package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.ConnectorId
import kotlinx.coroutines.flow.StateFlow

/**
 * Single-source-of-truth for one connector's storage status. The provider owns the TTL cache,
 * deduplicates concurrent refreshes, and emits [StorageStatus] through [status].
 *
 * Implementations must:
 * - Initialize [status] to [StorageStatus.Loading] with `lastKnown = null`.
 * - Record `lastFetchAt` on every refresh outcome (success, failure, Unsupported) so TTL is
 *   honored regardless of result. Otherwise an unsupported-account would refetch every upload.
 * - Rethrow [kotlinx.coroutines.CancellationException] from [refresh]; never swallow.
 * - Treat [refresh] as concurrency-safe — multiple parallel callers share one in-flight fetch.
 */
interface StorageStatusProvider {
    val connectorId: ConnectorId
    val status: StateFlow<StorageStatus>

    /**
     * Refresh the snapshot. No-op when the last attempt is younger than the implementation's TTL,
     * unless [forceFresh] is true.
     */
    suspend fun refresh(forceFresh: Boolean = false)

    /** Mark cache stale so the next [refresh] always fetches. */
    fun invalidate()
}
