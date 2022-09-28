package eu.darken.octi.sync.core.encryption

import com.google.crypto.tink.*
import com.google.crypto.tink.daead.DeterministicAeadConfig
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream


class PayloadEncryption constructor(private val keysetRaw: ByteString? = null) {

    private val keysetHandle by lazy {
        keysetRaw?.let {
            CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(keysetRaw.toByteArray()))
        } ?: KeysetHandle.generateNew(KeyTemplates.get("AES256_SIV"))
    }

    private val primitive by lazy { keysetHandle.getPrimitive(DeterministicAead::class.java) }

    fun exportKeyset(): ByteString {
        val output = ByteArrayOutputStream()
        output.use {
            CleartextKeysetHandle.getKeyset(keysetHandle).writeTo(it)
        }
        return output.toByteArray().toByteString()
    }

    fun encrypt(data: ByteString): ByteString = primitive.encryptDeterministically(data.toByteArray(), ByteArray(0))
        .toByteString()
        .also { log(TAG, VERBOSE) { "Encrypted: $data to $it" } }


    fun decrypt(data: ByteString): ByteString = primitive.decryptDeterministically(data.toByteArray(), ByteArray(0))
        .toByteString()
        .also { log(TAG, VERBOSE) { "Decrypted: $data to $it" } }

    companion object {
        private val TAG = logTag("Sync", "Crypto", "Payload")

        init {
            DeterministicAeadConfig.register()
            log(TAG) { "DeterministicAeadConfig registered" }
        }
    }
}