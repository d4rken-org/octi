package eu.darken.octi.sync.core

import eu.darken.octi.sync.core.encryption.CryptoCapabilities
import eu.darken.octi.sync.core.encryption.EncryptionMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes the local device's current capability tag set, used as the value of
 * [DeviceMetadata.capabilities] when publishing to peers via connectors.
 *
 * To declare a new capability namespace from the Android client: extend the buildSet
 * block to emit the namespace's `_reported` marker plus value tags for whatever this
 * device actually supports. See [Capability] for the authority semantics.
 */
@Singleton
class DeviceCapabilitiesProvider @Inject constructor(
    private val cryptoCapabilities: CryptoCapabilities,
) {

    fun current(): Set<String> = buildSet {
        // Encryption namespace
        add(Capability.ENCRYPTION_NAMESPACE_REPORTED)
        add(Capability.encryption(EncryptionMode.AES256_SIV))
        if (cryptoCapabilities.gcmSivAvailable) {
            add(Capability.encryption(EncryptionMode.AES256_GCM_SIV))
        }
        // Future namespaces: add NAMESPACE_REPORTED + value tags here.
    }
}
