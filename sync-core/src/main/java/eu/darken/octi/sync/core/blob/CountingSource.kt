package eu.darken.octi.sync.core.blob

import okio.Buffer
import okio.ForwardingSource
import okio.Source

/**
 * [Source] wrapper that reports cumulative bytes read after every successful `read`. Used to
 * surface upload progress at the plaintext layer when a downstream store doesn't expose a
 * chunk-level hook.
 */
class CountingSource(
    delegate: Source,
    private val onBytes: (bytesReadTotal: Long) -> Unit,
) : ForwardingSource(delegate) {

    private var bytesRead: Long = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val n = super.read(sink, byteCount)
        if (n > 0L) {
            bytesRead += n
            onBytes(bytesRead)
        }
        return n
    }
}
