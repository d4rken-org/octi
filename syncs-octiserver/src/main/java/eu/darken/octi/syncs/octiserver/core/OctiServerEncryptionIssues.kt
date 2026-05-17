package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.VersionCompat
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
        val minClientVersion = OctiServerEncryptionCompat.expectedMinClientVersion(keyset)
            ?: return emptyList()
        return metadata.mapNotNull { device ->
            if (device.deviceId == ownDeviceId) return@mapNotNull null
            if (!device.usesAndroidReleaseVersioning) return@mapNotNull null

            val version = device.version
            val knownTooOld = version != null && !VersionCompat.isAtLeast(version, minClientVersion)
            val unknownAndNotSyncing = version == null &&
                device.deviceId !in dataDeviceIds &&
                device.addedAt?.let { (now - it) >= gracePeriod } == true

            if (!knownTooOld && !unknownAndNotSyncing) return@mapNotNull null

            OctiServerIssue.EncryptionCompatibilityIncompatible(
                connectorId = connectorId,
                deviceId = device.deviceId,
                minClientVersion = minClientVersion,
            )
        }
    }
}
