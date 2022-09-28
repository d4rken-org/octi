package eu.darken.octi.sync.core.encryption

import android.os.Parcelable
import com.google.crypto.tink.*
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.ByteStringParcelizer
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream


class PayloadEncryption constructor(private val keySet: KeySet? = null) {

    private val keysetHandle by lazy {
        keySet?.let {
            CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(keySet.key.toByteArray()))
        } ?: KeysetHandle.generateNew(KeyTemplates.get(DEFAULT_KEY_TEMPLATE))
    }

    private val primitive by lazy { keysetHandle.getPrimitive(DeterministicAead::class.java) }

    fun exportKeyset(): KeySet {
        val output = ByteArrayOutputStream()
        output.use {
            CleartextKeysetHandle.getKeyset(keysetHandle).writeTo(it)
        }
        return KeySet(
            type = DEFAULT_KEY_TEMPLATE,
            key = output.toByteArray().toByteString()
        ).also { log(TAG, VERBOSE) { "exportKeyset(): $it" } }
    }

    fun encrypt(data: ByteString): ByteString = primitive.encryptDeterministically(data.toByteArray(), ByteArray(0))
        .toByteString()
        .also { log(TAG, VERBOSE) { "Encrypted: $data to $it" } }


    fun decrypt(data: ByteString): ByteString = primitive.decryptDeterministically(data.toByteArray(), ByteArray(0))
        .toByteString()
        .also { log(TAG, VERBOSE) { "Decrypted: $data to $it" } }


    @JsonClass(generateAdapter = true)
    @Parcelize
    @TypeParceler<ByteString, ByteStringParcelizer>
    data class KeySet(
        @Json(name = "type") val type: String,
        @Json(name = "key") val key: ByteString,
    ) : Parcelable {
        override fun toString(): String = "ShareCode(key=${key.base64().take(4)}...)"
    }

    companion object {
        private val TAG = logTag("Sync", "Crypto", "Payload")
        private const val DEFAULT_KEY_TEMPLATE = "AES256_SIV"

        init {
            DeterministicAeadConfig.register()
            log(TAG) { "DeterministicAeadConfig registered" }
        }
    }
}