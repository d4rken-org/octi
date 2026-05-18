package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.Capability
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.VersionCompat
import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import eu.darken.octi.sync.core.usesAndroidReleaseVersioning
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Pure helpers for computing OctiServer encryption-related connector issues.
 *
 * Extracted from OctiServerConnector so the logic is unit-testable without spinning up the
 * whole connector. All inputs are passed explicitly; the only static dependencies are the
 * platform-agnostic VersionCompat and OctiServerEncryptionCompat tables.
 */
internal object OctiServerEncryptionIssues {

    /**
     * Tri-valued classification of a peer's support for an encryption mode. Carries the
     * *reason* alongside the verdict so the caller can pick the right user-facing issue
     * variant (capability-mode vs Android-version remedy).
     */
    sealed interface PeerSupport {
        object Supported : PeerSupport
        object UnsupportedByCapabilities : PeerSupport
        data class UnsupportedByAndroidVersion(val minVersion: String) : PeerSupport
        object Unknown : PeerSupport
    }

    /**
     * Determines whether [peer] supports encryption [mode].
     *
     * Capability check is primary: if the peer publishes explicit capabilities, that
     * verdict wins regardless of platform or version. Otherwise we fall back to the
     * Android-version heuristic, which only applies to Android peers and only the GCM-SIV
     * gate (SIV has no minimum Android version — every Android client could read SIV).
     */
    fun peerSupports(peer: DeviceMetadata, mode: EncryptionMode): PeerSupport {
        when (Capability.supportsEncryption(peer.capabilities, mode)) {
            true -> return PeerSupport.Supported
            false -> return PeerSupport.UnsupportedByCapabilities
            null -> Unit  // capabilities unknown for this namespace; fall through to version fallback
        }
        if (!peer.usesAndroidReleaseVersioning) return PeerSupport.Unknown
        return when (mode) {
            EncryptionMode.AES256_GCM_SIV ->
                if (VersionCompat.isAtLeast(peer.version, OctiServerEncryptionCompat.MIN_GCM_SIV_CLIENT_VERSION)) {
                    PeerSupport.Supported
                } else if (peer.version != null) {
                    PeerSupport.UnsupportedByAndroidVersion(OctiServerEncryptionCompat.MIN_GCM_SIV_CLIENT_VERSION)
                } else {
                    PeerSupport.Unknown
                }
            EncryptionMode.AES256_SIV -> PeerSupport.Supported
        }
    }

    fun buildLegacyEncryptionIssues(
        connectorId: ConnectorId,
        metadata: List<DeviceMetadata>,
        isGcmSiv: Boolean,
    ): List<ConnectorIssue> {
        if (isGcmSiv) return emptyList()
        return metadata.mapNotNull { device ->
            if (!device.usesAndroidReleaseVersioning) return@mapNotNull null
            if (!VersionCompat.isAtLeast(device.version, OctiServerEncryptionCompat.MIN_GCM_SIV_CLIENT_VERSION)) {
                return@mapNotNull null
            }
            OctiServerIssue.LegacyEncryptionAccount(
                connectorId = connectorId,
                deviceId = device.deviceId,
            )
        }
    }

    fun buildEncryptionCompatibilityIssues(
        connectorId: ConnectorId,
        ownDeviceId: DeviceId,
        keyset: PayloadEncryption.KeySet,
        metadata: List<DeviceMetadata>,
        dataDeviceIds: Set<DeviceId>,
        now: Instant,
        gracePeriod: Duration,
    ): List<ConnectorIssue> {
        // The required mode is whatever the account currently uses. We check capability
        // for that mode against every peer — including SIV accounts, so a future peer
        // that explicitly lacks SIV would be flagged. (Previously this code short-circuited
        // on SIV accounts via expectedMinClientVersion returning null.)
        val requiredMode = EncryptionMode.fromTypeString(keyset.type) ?: return emptyList()
        return metadata.mapNotNull { device ->
            if (device.deviceId == ownDeviceId) return@mapNotNull null
            when (val support = peerSupports(device, requiredMode)) {
                PeerSupport.Supported -> null
                PeerSupport.UnsupportedByCapabilities ->
                    OctiServerIssue.EncryptionCompatibilityIncompatible.UnsupportedEncryptionMode(
                        connectorId = connectorId,
                        deviceId = device.deviceId,
                        requiredMode = requiredMode,
                    )
                is PeerSupport.UnsupportedByAndroidVersion ->
                    OctiServerIssue.EncryptionCompatibilityIncompatible.AndroidClientTooOld(
                        connectorId = connectorId,
                        deviceId = device.deviceId,
                        minClientVersion = support.minVersion,
                    )
                PeerSupport.Unknown -> {
                    // Preserve PR #308's grace-period heuristic strictly for Android-shaped
                    // peers (platform == "android" or null for v0.8.1-era legacy clients).
                    // Non-Android peers with unknown capabilities stay unflagged — their
                    // grace period concept doesn't apply, and version-string fallback
                    // doesn't either.
                    val unknownAndNotSyncing = device.capabilities == null &&
                        device.version == null &&
                        device.usesAndroidReleaseVersioning &&
                        device.deviceId !in dataDeviceIds &&
                        device.addedAt?.let { (now - it) >= gracePeriod } == true
                    if (!unknownAndNotSyncing) null
                    else OctiServerIssue.EncryptionCompatibilityIncompatible.AndroidClientTooOld(
                        connectorId = connectorId,
                        deviceId = device.deviceId,
                        minClientVersion = OctiServerEncryptionCompat.MIN_GCM_SIV_CLIENT_VERSION,
                    )
                }
            }
        }
    }
}
