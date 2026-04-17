package eu.darken.octi.sync.core.blob

/**
 * Transfer progress for a blob upload or download. `bytesTotal` may be the ciphertext size for
 * uploads (what the network sees) or the plaintext size for downloads (what the caller wrote to
 * the sink) — consumers should treat it as a best-effort total for display.
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
