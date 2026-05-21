package eu.darken.octi.sync.core.encryption

import android.os.Parcelable
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AesGcmSivKeyManager
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.ByteStringParcelizer
import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString
import okio.ByteString.Companion.toByteString


class PayloadEncryption(
    private val keySet: KeySet? = null,
    private val useLegacyEncryption: Boolean = false,
) {

    // smoke: cross-repo-verify gate touch (no behavior change)
    init {
        // Process-global JCE provider install + Tink configuration registration must
        // happen before any keysetHandle / primitive call. CryptoBootstrap owns that;
        // calling ensureInitialized forces its class load on first construction so direct
        // instantiation sites (tests, OctiServerConnector, etc.) are covered without
        // relying on App.onCreate having run first.
        CryptoBootstrap.ensureInitialized()
    }

    private val keysetHandle by lazy {
        keySet?.let {
            TinkProtoKeysetFormat.parseKeyset(keySet.key.toByteArray(), InsecureSecretKeyAccess.get())
        } ?: if (useLegacyEncryption) {
            KeysetHandle.generateNew(KeyTemplates.get(EncryptionMode.AES256_SIV.typeString))
        } else {
            KeysetHandle.generateNew(AesGcmSivKeyManager.aes256GcmSivTemplate())
        }
    }

    private val isSiv: Boolean
        get() = EncryptionMode.fromTypeString(keySet?.type)?.isLegacy == true || (keySet == null && useLegacyEncryption)

    private val aeadPrimitive by lazy { keysetHandle.getPrimitive(Aead::class.java) }
    private val daeadPrimitive by lazy { keysetHandle.getPrimitive(DeterministicAead::class.java) }

    fun exportKeyset(): KeySet {
        val serialized = TinkProtoKeysetFormat.serializeKeyset(keysetHandle, InsecureSecretKeyAccess.get())
        val type = keySet?.type
            ?: if (useLegacyEncryption) EncryptionMode.AES256_SIV.typeString else EncryptionMode.AES256_GCM_SIV.typeString
        return KeySet(
            type = type,
            key = serialized.toByteString(),
        ).also { log(TAG, VERBOSE) { "exportKeyset(): $it" } }
    }

    /**
     * @param associatedData only honored for GCM-SIV keysets. Legacy SIV keysets ignore this
     * parameter (existing data was encrypted without AD, so changing this would break decryption).
     */
    fun encrypt(data: ByteString, associatedData: ByteArray = ByteArray(0)): ByteString = if (isSiv) {
        daeadPrimitive.encryptDeterministically(data.toByteArray(), ByteArray(0))
    } else {
        aeadPrimitive.encrypt(data.toByteArray(), associatedData)
    }
        .toByteString()
        .also { log(TAG, VERBOSE) { "Encrypted: $data to $it" } }

    /** @see encrypt for [associatedData] behavior with legacy SIV keysets. */
    fun decrypt(data: ByteString, associatedData: ByteArray = ByteArray(0)): ByteString = if (isSiv) {
        daeadPrimitive.decryptDeterministically(data.toByteArray(), ByteArray(0))
    } else {
        aeadPrimitive.decrypt(data.toByteArray(), associatedData)
    }
        .toByteString()
        .also { log(TAG, VERBOSE) { "Decrypted: $data to $it" } }


    @Serializable
    @Parcelize
    @TypeParceler<ByteString, ByteStringParcelizer>
    data class KeySet(
        @SerialName("type") val type: String,
        @Serializable(with = ByteStringSerializer::class) @SerialName("key") val key: ByteString,
    ) : Parcelable {
        override fun toString(): String = "ShareCode(key=${key.base64().take(4)}...)"
    }

    companion object {
        private val TAG = logTag("Sync", "Crypto", "Payload")
    }
}
