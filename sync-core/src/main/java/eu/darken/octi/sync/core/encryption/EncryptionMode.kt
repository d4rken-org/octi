package eu.darken.octi.sync.core.encryption

/**
 * Encryption modes for [PayloadEncryption].
 * [typeString] is the wire-format identifier stored in [PayloadEncryption.KeySet.type].
 */
enum class EncryptionMode(val typeString: String) {
    /** Default for new accounts. Nonce-misuse-resistant AEAD, supports associated data. */
    AES256_GCM_SIV("AES256_GCM_SIV"),

    /** Legacy deterministic encryption. No associated data support. Used by app versions before v1.0.0. */
    AES256_SIV("AES256_SIV"),
    ;

    val isLegacy: Boolean get() = this == AES256_SIV

    companion object {
        fun fromTypeString(type: String?): EncryptionMode? = entries.find { it.typeString == type }
    }
}
