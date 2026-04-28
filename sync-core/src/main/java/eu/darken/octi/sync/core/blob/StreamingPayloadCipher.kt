package eu.darken.octi.sync.core.blob

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import okio.BufferedSink
import okio.Sink
import okio.Source
import okio.buffer
import java.io.OutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AEAD encryption for blob content (OctiServer only).
 * Uses Tink's [AesGcmHkdfStreaming] primitive with a key derived via HKDF-SHA256
 * from the existing sync encryption keyset. This ensures all devices sharing the
 * same [PayloadEncryption.KeySet] agree on the same streaming key — no extra
 * key exchange or migration required.
 *
 * Legacy AES256_SIV keysets are rejected — streaming AEAD is not safe with
 * deterministic key material.
 */
class StreamingPayloadCipher(keySet: PayloadEncryption.KeySet) {

    private val streamingAead: AesGcmHkdfStreaming

    init {
        val mode = EncryptionMode.fromTypeString(keySet.type)
        require(mode == EncryptionMode.AES256_GCM_SIV) {
            "Only AES256_GCM_SIV keysets are supported for blob streaming encryption (was: ${keySet.type})"
        }

        val derivedKey = hkdfSha256(
            ikm = keySet.key.toByteArray(),
            salt = HKDF_SALT,
            info = HKDF_INFO,
            length = KEY_SIZE_BYTES,
        )

        streamingAead = AesGcmHkdfStreaming(
            derivedKey,
            "HmacSha256",
            KEY_SIZE_BYTES,
            SEGMENT_SIZE,
            0,
        )

        log(TAG, VERBOSE) { "StreamingPayloadCipher initialized (mode=${keySet.type})" }
    }

    /**
     * Returns the number of ciphertext bytes that [encrypt] would produce for a plaintext
     * of [plaintextBytes] bytes, using the configured streaming-AEAD parameters. Delegates to
     * Tink's own `expectedCiphertextSize` so the formula stays in lock-step with whatever
     * header/segment/tag layout the underlying primitive uses.
     *
     * Used by the upload pipeline to declare ciphertext size to the server *before* any
     * bytes have been encrypted, so we don't need to encrypt-to-temp first.
     */
    fun ciphertextSize(plaintextBytes: Long): Long {
        require(plaintextBytes >= 0) { "plaintextBytes must be non-negative (was $plaintextBytes)" }
        return streamingAead.expectedCiphertextSize(plaintextBytes)
    }

    /**
     * Encrypt [source] into [sink] with [associatedData] binding.
     */
    fun encrypt(source: Source, sink: Sink, associatedData: ByteArray) {
        log(TAG, VERBOSE) { "encrypt(aad=${associatedData.size}B)" }
        val bufferedSink = sink.buffer()
        source.buffer().inputStream().use { plainIn ->
            streamingAead.newEncryptingStream(NoCloseBufferedSinkOutputStream(bufferedSink), associatedData).use { encOut ->
                plainIn.copyTo(encOut)
            }
        }
        bufferedSink.emit()
    }

    /**
     * Decrypt [source] into [sink] with [associatedData] binding.
     *
     * Streaming AEAD contract: Tink verifies and emits plaintext one segment at a time
     * (SEGMENT_SIZE = 1 MB). If a later segment's tag fails, earlier segments' plaintext
     * has already been written to [sink]. Callers MUST treat [sink] as valid only when
     * this function returns without throwing — e.g. write to a temp file and re-verify
     * a full-file checksum before exposing the result, don't expose partial output.
     */
    fun decrypt(source: Source, sink: Sink, associatedData: ByteArray) {
        log(TAG, VERBOSE) { "decrypt(aad=${associatedData.size}B)" }
        val bufferedSink = sink.buffer()
        streamingAead.newDecryptingStream(source.buffer().inputStream(), associatedData).use { decIn ->
            decIn.copyTo(NoCloseBufferedSinkOutputStream(bufferedSink))
        }
        bufferedSink.emit()
    }

    companion object {
        private val TAG = logTag("Sync", "Crypto", "StreamingCipher")

        private const val KEY_SIZE_BYTES = 32
        private const val SEGMENT_SIZE = 1 * 1024 * 1024 // 1 MB segments
        private val HKDF_SALT = "octi-blob".toByteArray()
        private val HKDF_INFO = "octi-blob-stream-v1".toByteArray()

        /**
         * HKDF-SHA256 extract-and-expand. Returns [length] bytes of derived key material.
         * Uses javax.crypto.Mac directly to avoid pulling a separate HKDF library.
         */
        internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            require(length <= 32) { "HKDF-SHA256 can produce at most 32 bytes per expand step" }

            // Extract: PRK = HMAC-SHA256(salt, IKM)
            val extractMac = Mac.getInstance("HmacSHA256")
            extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
            val prk = extractMac.doFinal(ikm)

            // Expand: OKM = HMAC-SHA256(PRK, info || 0x01)
            val expandMac = Mac.getInstance("HmacSHA256")
            expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
            expandMac.update(info)
            expandMac.update(0x01.toByte())
            val okm = expandMac.doFinal()

            return okm.copyOf(length)
        }
    }

    private class NoCloseBufferedSinkOutputStream(
        private val sink: BufferedSink,
    ) : OutputStream() {
        override fun write(oneByte: Int) {
            sink.writeByte(oneByte)
        }

        override fun write(data: ByteArray, offset: Int, byteCount: Int) {
            sink.write(data, offset, byteCount)
        }

        override fun flush() {
            sink.flush()
        }

        override fun close() {
            sink.flush()
        }
    }
}
