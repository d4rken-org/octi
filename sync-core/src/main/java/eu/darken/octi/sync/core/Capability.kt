package eu.darken.octi.sync.core

import eu.darken.octi.sync.core.encryption.EncryptionMode

/**
 * Central registry of capability tags for [DeviceMetadata.capabilities].
 *
 * Tag format: `<namespace>:<value>` (e.g. `encryption:AES256_GCM_SIV`). Each namespace
 * has a sentinel marker `<namespace>:_reported` emitted by any producer that participates
 * in that namespace; consumers use it to distinguish "peer doesn't speak this namespace"
 * (unknown) from "peer speaks it and explicitly doesn't support this value" (known
 * unsupported). Authority rules:
 *
 * - `caps == null` → peer reports no capabilities at all → unknown
 * - non-null, `X:_reported` absent → namespace X unknown for this peer
 * - non-null, `X:_reported` present, value tag absent → known unsupported
 * - non-null, `X:_reported` present, value tag present → known supported
 *
 * To add a new namespace:
 *   1. Add a `<X>_NAMESPACE_REPORTED` constant.
 *   2. Add tag-construction helpers (`<x>(value)`).
 *   3. Add a `supports<X>(caps, value): Boolean?` semantic helper that handles authority.
 *   4. Update [DeviceCapabilitiesProvider] to publish the marker and value tags supported
 *      by the local device.
 */
object Capability {

    // --- encryption namespace ---

    const val ENCRYPTION_NAMESPACE_REPORTED = "encryption:_reported"

    fun encryption(mode: EncryptionMode): String = "encryption:${mode.typeString}"

    /**
     * Tri-state encryption-mode check:
     *   - null  → caps null, OR peer doesn't participate in the encryption namespace
     *   - true  → peer participates and reports support for [mode]
     *   - false → peer participates and explicitly doesn't support [mode]
     */
    fun supportsEncryption(caps: Set<String>?, mode: EncryptionMode): Boolean? {
        if (caps == null) return null
        if (ENCRYPTION_NAMESPACE_REPORTED !in caps) return null
        return encryption(mode) in caps
    }
}
