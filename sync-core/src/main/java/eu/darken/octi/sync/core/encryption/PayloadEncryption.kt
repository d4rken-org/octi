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
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
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

        // RFC 8452 Appendix C.1 vector 1: AES-128-GCM-SIV, key=0x01||..., nonce=0x03||...,
        // empty plaintext+AAD → tag dc20e2d83f25705bb49e439eca56de25. Used to distinguish
        // working AES-GCM-SIV from a provider that silently returns AES-GCM under the same
        // transformation name (observed on Android API ≤ 29 system Conscrypt).
        private val GCM_SIV_RFC8452_KEY = ByteArray(16).also { it[0] = 1 }
        private val GCM_SIV_RFC8452_NONCE = ByteArray(12).also { it[0] = 3 }
        private val GCM_SIV_RFC8452_EXPECTED = byteArrayOf(
            0xDC.toByte(), 0x20.toByte(), 0xE2.toByte(), 0xD8.toByte(),
            0x3F.toByte(), 0x25.toByte(), 0x70.toByte(), 0x5B.toByte(),
            0xB4.toByte(), 0x9E.toByte(), 0x43.toByte(), 0x9E.toByte(),
            0xCA.toByte(), 0x56.toByte(), 0xDE.toByte(), 0x25.toByte(),
        )

        // Tink's EngineFactory.AndroidPolicy resolves Cipher providers by *name* in this
        // exact order before falling through to default JCE lookup. Either being broken
        // (or returning a wrong-algorithm cipher under "AES/GCM-SIV/NoPadding") would
        // poison Tink's primitive construction.
        private val TINK_PREFERRED_PROVIDER_NAMES = listOf("GmsCore_OpenSSL", "AndroidOpenSSL")

        /**
         * True when [PayloadEncryption] can produce and decrypt AES-GCM-SIV ciphertext on
         * this device. Set during companion init by [verifyTinkAesGcmSivWorks] after
         * provider install + Tink registration. Callers (e.g. account creation) should fall
         * back to [EncryptionMode.AES256_SIV] when this is `false` to avoid producing a
         * keyset that can never be used.
         */
        @Volatile
        var gcmSivAvailable: Boolean = false
            private set

        init {
            val vmName = System.getProperty("java.vm.name") ?: ""
            val isAndroidRuntime = vmName.contains("Dalvik", ignoreCase = true)

            // Feature-detect whether the platform JCE serves a *working* AES/GCM-SIV/NoPadding.
            // The naive Cipher.getInstance probe is insufficient: on API ≤ 29 the system
            // Conscrypt accepts the transformation string but returns AES-GCM (Tink later
            // rejects this via its internal test-vector check, surfacing as "AES GCM SIV
            // cipher is not available or is invalid"). We run the same RFC 8452 vector here
            // up-front so the install gate is correct.
            if (!platformHasWorkingGcmSiv()) {
                if (isAndroidRuntime) installBundledConscrypt() else installBouncyCastle()
            }

            DeterministicAeadConfig.register()
            AeadConfig.register()
            AesGcmSivKeyManager.register(true)
            StreamingAeadConfig.register()
            log(TAG) { "DeterministicAeadConfig + AeadConfig + AesGcmSiv + StreamingAead registered" }

            // Postcondition runs the actual Tink AEAD path (not just JCE Cipher lookup) so
            // it covers Tink's named-provider resolution exactly. Result is exposed via
            // [gcmSivAvailable] for new-keyset gating.
            gcmSivAvailable = verifyTinkAesGcmSivWorks()
            if (gcmSivAvailable) {
                log(TAG) { "Tink AES-GCM-SIV round-trip verified" }
            } else {
                log(TAG, ERROR) { "Tink AES-GCM-SIV round-trip FAILED — new accounts will fall back to legacy SIV" }
            }
        }

        internal fun platformHasWorkingGcmSiv(): Boolean = try {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM-SIV/NoPadding")
            cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(GCM_SIV_RFC8452_KEY, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, GCM_SIV_RFC8452_NONCE),
            )
            cipher.doFinal(ByteArray(0)).contentEquals(GCM_SIV_RFC8452_EXPECTED)
        } catch (_: Throwable) {
            false
        }

        // Mirrors Tink's EngineFactory.AndroidPolicy lookup order so we can detect
        // "GmsCore_OpenSSL is broken even though AndroidOpenSSL is healthy" — Tink would
        // hit the broken provider first.
        private fun verifyTinkAesGcmSivWorks(): Boolean = try {
            val testKeyset = KeysetHandle.generateNew(AesGcmSivKeyManager.aes256GcmSivTemplate())
            val aead = testKeyset.getPrimitive(Aead::class.java)
            val ciphertext = aead.encrypt("octi-init-probe".toByteArray(), null)
            aead.decrypt(ciphertext, null).contentEquals("octi-init-probe".toByteArray())
        } catch (_: Throwable) {
            false
        }

        private fun installBundledConscrypt() {
            // Tink's EngineFactory.AndroidPolicy looks up Cipher providers by *name* in
            // [TINK_PREFERRED_PROVIDER_NAMES] order before falling through to default JCE
            // lookup. Installing only at name "Conscrypt" leaves a broken system provider
            // in place, so Tink would still pick up the platform cipher. We replace each
            // preferred name with our bundled Conscrypt — `removeProvider` is a no-op when
            // the name is absent, so this is safe regardless of the device's existing
            // provider set.
            try {
                val conscryptClass = Class.forName("org.conscrypt.Conscrypt")
                val newProvider = conscryptClass.getMethod("newProvider", String::class.java)
                for (name in TINK_PREFERRED_PROVIDER_NAMES) {
                    val provider = newProvider.invoke(null, name) as java.security.Provider
                    java.security.Security.removeProvider(name)
                    java.security.Security.insertProviderAt(provider, 1)
                }
                log(TAG) { "Installed bundled Conscrypt as ${TINK_PREFERRED_PROVIDER_NAMES.joinToString("+")} (Android API ${android.os.Build.VERSION.SDK_INT})" }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                // Unwrap to surface the real failure — most commonly UnsatisfiedLinkError
                // or ExceptionInInitializerError from Conscrypt's native-lib bootstrap.
                val cause = e.targetException ?: e
                log(TAG, ERROR) { "Conscrypt provider init failed (${cause.javaClass.simpleName}): ${cause.asLog()}" }
            } catch (e: ClassNotFoundException) {
                log(TAG, WARN) { "Conscrypt class not found at runtime: ${e.message}" }
            } catch (e: ReflectiveOperationException) {
                log(TAG, ERROR) { "Failed to instantiate Conscrypt provider: ${e.asLog()}" }
            } catch (e: LinkageError) {
                log(TAG, ERROR) { "Conscrypt native lib missing or incompatible: ${e.asLog()}" }
            } catch (e: SecurityException) {
                log(TAG, ERROR) { "SecurityManager denied Conscrypt registration: ${e.asLog()}" }
            }
        }

        private fun installBouncyCastle() {
            // JVM unit tests rely on BouncyCastle for AES-GCM-SIV. The Android runtime
            // never takes this branch — it uses Conscrypt above.
            try {
                val bcClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                val provider = bcClass.getDeclaredConstructor().newInstance() as java.security.Provider
                java.security.Security.insertProviderAt(provider, 1)
            } catch (_: ClassNotFoundException) {
                // BouncyCastle not on classpath
            } catch (e: ReflectiveOperationException) {
                log(TAG, ERROR) { "Failed to instantiate BouncyCastle provider: ${e.asLog()}" }
            }
        }
    }
}
