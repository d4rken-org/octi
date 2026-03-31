@file:UseSerializers(ByteStringSerializer::class)

package eu.darken.octi.sync.core.encryption

import android.os.Parcelable
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmSivKeyManager
import com.google.crypto.tink.daead.DeterministicAeadConfig
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.serialization.ByteStringParcelizer
import eu.darken.octi.common.serialization.serializer.ByteStringSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import okio.ByteString
import okio.ByteString.Companion.toByteString


class PayloadEncryption constructor(
    private val keySet: KeySet? = null,
    private val useLegacyEncryption: Boolean = false,
) {

    private val keysetHandle by lazy {
        keySet?.let {
            TinkProtoKeysetFormat.parseKeyset(keySet.key.toByteArray(), InsecureSecretKeyAccess.get())
        } ?: if (useLegacyEncryption) {
            KeysetHandle.generateNew(KeyTemplates.get(LEGACY_KEY_TEMPLATE))
        } else {
            KeysetHandle.generateNew(AesGcmSivKeyManager.aes256GcmSivTemplate())
        }
    }

    private val isSiv: Boolean
        get() = keySet?.type == LEGACY_KEY_TEMPLATE || (keySet == null && useLegacyEncryption)

    private val aeadPrimitive by lazy { keysetHandle.getPrimitive(Aead::class.java) }
    private val daeadPrimitive by lazy { keysetHandle.getPrimitive(DeterministicAead::class.java) }

    fun exportKeyset(): KeySet {
        val serialized = TinkProtoKeysetFormat.serializeKeyset(keysetHandle, InsecureSecretKeyAccess.get())
        val type = keySet?.type ?: if (useLegacyEncryption) LEGACY_KEY_TEMPLATE else DEFAULT_KEY_TEMPLATE
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
        @SerialName("key") val key: ByteString,
    ) : Parcelable {
        override fun toString(): String = "ShareCode(key=${key.base64().take(4)}...)"
    }

    companion object {
        private val TAG = logTag("Sync", "Crypto", "Payload")
        private const val DEFAULT_KEY_TEMPLATE = "AES256_GCM_SIV"
        private const val LEGACY_KEY_TEMPLATE = "AES256_SIV"

        init {
            // Register BouncyCastle for AES-GCM-SIV support in JVM unit tests.
            // On Android (Dalvik/ART), the platform provider already handles it — skip
            // to avoid overriding Conscrypt at JCA position 1.
            val vmName = System.getProperty("java.vm.name") ?: ""
            val isAndroidRuntime = vmName.contains("Dalvik", ignoreCase = true)
            if (!isAndroidRuntime) {
                try {
                    val bcClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    val provider = bcClass.getDeclaredConstructor().newInstance() as java.security.Provider
                    java.security.Security.insertProviderAt(provider, 1)
                } catch (_: ClassNotFoundException) {
                    // BouncyCastle not on classpath
                }
            }
            DeterministicAeadConfig.register()
            AeadConfig.register()
            AesGcmSivKeyManager.register(true)
            log(TAG) { "DeterministicAeadConfig + AeadConfig + AesGcmSiv registered" }
        }
    }
}
