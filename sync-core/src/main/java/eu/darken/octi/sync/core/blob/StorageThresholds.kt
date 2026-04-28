package eu.darken.octi.sync.core.blob

/** Considered "low" when less than this fraction of [StorageSnapshot.totalBytes] is available. */
const val STORAGE_LOW_AVAILABLE_FRACTION = 0.10

/**
 * `true` when [StorageSnapshot.availableBytes] / [StorageSnapshot.totalBytes] is strictly below
 * [STORAGE_LOW_AVAILABLE_FRACTION]. Returns `false` when [StorageSnapshot.totalBytes] <= 0
 * (unbounded / unknown) so we don't warn for backends that can't quote a quota.
 */
fun StorageSnapshot.isLowStorage(): Boolean =
    totalBytes > 0 && availableBytes.toDouble() / totalBytes < STORAGE_LOW_AVAILABLE_FRACTION
