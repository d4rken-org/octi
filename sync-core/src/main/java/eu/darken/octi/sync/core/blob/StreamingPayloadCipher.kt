package eu.darken.octi.sync.core.blob

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import okio.FileSystem
import okio.Path
import java.io.FileInputStream
import java.io.FileOutputStream
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
        require(mode != EncryptionMode.AES256_SIV) {
            "Legacy AES256_SIV keysets are not supported for blob streaming encryption"
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
     * Encrypt [plainFile] to [cipherFile] with [associatedData] binding.
     * The caller owns both files and is responsible for cleanup.
     */
    fun encryptToFile(plainFile: Path, cipherFile: Path, associatedData: ByteArray) {
        log(TAG, VERBOSE) { "encryptToFile(${plainFile.name} → ${cipherFile.name}, aad=${associatedData.size}B)" }
        FileInputStream(plainFile.toFile()).use { plainIn ->
            FileOutputStream(cipherFile.toFile()).use { cipherOut ->
                streamingAead.newEncryptingStream(cipherOut, associatedData).use { encOut ->
                    plainIn.copyTo(encOut)
                }
            }
        }
    }

    /**
     * Decrypt [cipherFile] to [plainFile] with [associatedData] binding.
     * The caller owns both files and is responsible for cleanup.
     */
    fun decryptToFile(cipherFile: Path, plainFile: Path, associatedData: ByteArray) {
        log(TAG, VERBOSE) { "decryptToFile(${cipherFile.name} → ${plainFile.name}, aad=${associatedData.size}B)" }
        FileInputStream(cipherFile.toFile()).use { cipherIn ->
            streamingAead.newDecryptingStream(cipherIn, associatedData).use { decIn ->
                FileOutputStream(plainFile.toFile()).use { plainOut ->
                    decIn.copyTo(plainOut)
                }
            }
        }
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
}
