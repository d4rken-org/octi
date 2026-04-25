package eu.darken.octi.sync.core.blob

/**
 * Transfer progress for a blob upload or download.
 *
 * Invariant: [bytesTotal] is always **plaintext** — the size of the original file on the
 * sender's device. [bytesTransferred] is plaintext-equivalent: encrypting backends scale
 * their on-the-wire ciphertext progress to the corresponding plaintext fraction so a single
 * "X / Y MiB" readout matches the file picker.
 *
 * [fraction] is a convenience clamped to 0.0..1.0. When `bytesTotal <= 0` it reports 0.
 */
data class BlobProgress(
    val bytesTransferred: Long,
    val bytesTotal: Long,
) {
    val fraction: Float
        get() = if (bytesTotal <= 0L) 0f else (bytesTransferred.toFloat() / bytesTotal).coerceIn(0f, 1f)
}

/**
 * Callback for reporting blob transfer progress. Implementations must be safe to call from any
 * thread and should be quick — invoked on the hot path of an upload chunk or a download read.
 */
typealias BlobProgressCallback = (progress: BlobProgress) -> Unit
