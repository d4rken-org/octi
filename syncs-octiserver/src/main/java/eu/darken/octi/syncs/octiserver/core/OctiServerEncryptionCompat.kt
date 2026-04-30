package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption

object OctiServerEncryptionCompat {
    const val MIN_GCM_SIV_CLIENT_VERSION = "1.0.0"

    fun expectedMinClientVersion(keyset: PayloadEncryption.KeySet): String? =
        when (EncryptionMode.fromTypeString(keyset.type)) {
            EncryptionMode.AES256_GCM_SIV -> MIN_GCM_SIV_CLIENT_VERSION
            EncryptionMode.AES256_SIV,
            null -> null
        }
}
