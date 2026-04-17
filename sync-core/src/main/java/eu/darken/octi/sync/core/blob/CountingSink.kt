package eu.darken.octi.sync.core.blob

import okio.Buffer
import okio.ForwardingSink
import okio.Sink

/**
 * [Sink] wrapper that reports cumulative bytes written after every successful `write`. Used to
 * surface download progress at the plaintext layer.
 */
class CountingSink(
    delegate: Sink,
    private val onBytes: (bytesWrittenTotal: Long) -> Unit,
) : ForwardingSink(delegate) {

    private var bytesWritten: Long = 0L

    override fun write(source: Buffer, byteCount: Long) {
        super.write(source, byteCount)
        if (byteCount > 0L) {
            bytesWritten += byteCount
            onBytes(bytesWritten)
        }
    }
}
