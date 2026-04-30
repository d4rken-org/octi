package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption

object AccountCompatibility {
    const val MIN_ENCRYPTION_V2_CLIENT_VERSION = "1.0.0"

    fun expectedMinClientVersion(keyset: PayloadEncryption.KeySet): String? =
        when (EncryptionMode.fromTypeString(keyset.type)) {
            EncryptionMode.AES256_GCM_SIV -> MIN_ENCRYPTION_V2_CLIENT_VERSION
            EncryptionMode.AES256_SIV,
            null -> null
        }
}
