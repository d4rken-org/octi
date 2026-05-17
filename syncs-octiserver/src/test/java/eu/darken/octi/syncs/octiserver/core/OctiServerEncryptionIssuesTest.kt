package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class OctiServerEncryptionIssuesTest : BaseTest() {

    private val connectorId = ConnectorId(ConnectorType.OCTISERVER, "host", "acc")
    private val ownDeviceId = DeviceId("self")
    private val gracePeriod = SyncSettings.FIRST_SYNC_GRACE_PERIOD
    private val now = Clock.System.now()

    private val gcmSivKeyset = PayloadEncryption.KeySet(
        type = EncryptionMode.AES256_GCM_SIV.typeString,
        key = "dummy".encodeUtf8(),
    )
    private val sivKeyset = PayloadEncryption.KeySet(
        type = EncryptionMode.AES256_SIV.typeString,
        key = "dummy".encodeUtf8(),
    )

    private fun peer(
        id: String = "peer",
        version: String? = null,
        platform: String? = "android",
        addedAt: kotlin.time.Instant? = now - 1.minutes,
    ) = DeviceMetadata(
        deviceId = DeviceId(id),
        version = version,
        platform = platform,
        addedAt = addedAt,
    )

    @Nested
    inner class EncryptionCompatibility {

        @Test
        fun `flagged when Android peer has known too-old version`() {
            val p = peer(version = "0.14.0", platform = "android")
            val issues = OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            )
            issues shouldContainExactly listOf(
                OctiServerIssue.EncryptionCompatibilityIncompatible(
                    connectorId = connectorId,
                    deviceId = p.deviceId,
                    minClientVersion = OctiServerEncryptionCompat.MIN_GCM_SIV_CLIENT_VERSION,
                ),
            )
        }

        @Test
        fun `not flagged when Android peer is at min version`() {
            val p = peer(version = "1.0.0", platform = "android")
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `flagged when Android peer has null version, past grace, not syncing`() {
            // Preserves the legacy-Android heuristic: old client that hasn't yet upgraded
            // to a version-reporting build.
            val p = peer(version = null, platform = "android", addedAt = now - gracePeriod - 1.minutes)
            val issues = OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            )
            issues.shouldNotBeEmpty()
        }

        @Test
        fun `not flagged when Android peer has null version within grace`() {
            val p = peer(version = null, platform = "android", addedAt = now - 1.minutes)
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `headline fix - web peer with garbage version never flagged`() {
            val p = peer(version = "octi-web/0.0.0", platform = "web", addedAt = now - gracePeriod - 1.minutes)
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `conceptual fix - desktop peer with parseable low semver never flagged`() {
            val p = peer(version = "0.0.0", platform = "desktop", addedAt = now - gracePeriod - 1.minutes)
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `non-Android peer with null version is also never flagged`() {
            val p = peer(version = null, platform = "web", addedAt = now - gracePeriod - 1.minutes)
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `own device is never flagged`() {
            val p = peer(id = ownDeviceId.id, version = "0.5.0", platform = "android")
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `legacy keyset returns empty (no min version applies)`() {
            val p = peer(version = "0.5.0", platform = "android")
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = sivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }
    }

    @Nested
    inner class LegacyEncryption {

        @Test
        fun `flagged when Android peer is at GCM-SIV-capable version on legacy account`() {
            val p = peer(version = "1.0.0", platform = "android")
            val issues = OctiServerEncryptionIssues.buildLegacyEncryptionIssues(
                connectorId = connectorId,
                metadata = listOf(p),
                isGcmSiv = false,
            )
            issues shouldContainExactly listOf(
                OctiServerIssue.LegacyEncryptionAccount(
                    connectorId = connectorId,
                    deviceId = p.deviceId,
                ),
            )
        }

        @Test
        fun `not flagged when account is already GCM-SIV`() {
            val p = peer(version = "1.0.0", platform = "android")
            OctiServerEncryptionIssues.buildLegacyEncryptionIssues(
                connectorId = connectorId,
                metadata = listOf(p),
                isGcmSiv = true,
            ).shouldBeEmpty()
        }

        @Test
        fun `not flagged when Android peer is below GCM-SIV-capable version`() {
            val p = peer(version = "0.14.0", platform = "android")
            OctiServerEncryptionIssues.buildLegacyEncryptionIssues(
                connectorId = connectorId,
                metadata = listOf(p),
                isGcmSiv = false,
            ).shouldBeEmpty()
        }

        @Test
        fun `not flagged for web peer regardless of version`() {
            // Conceptual fix: web release numbers don't predict Android capability.
            val p = peer(version = "2.0.0", platform = "web")
            OctiServerEncryptionIssues.buildLegacyEncryptionIssues(
                connectorId = connectorId,
                metadata = listOf(p),
                isGcmSiv = false,
            ).shouldBeEmpty()
        }

        @Test
        fun `not flagged for desktop peer regardless of version`() {
            val p = peer(version = "5.0.0", platform = "desktop")
            OctiServerEncryptionIssues.buildLegacyEncryptionIssues(
                connectorId = connectorId,
                metadata = listOf(p),
                isGcmSiv = false,
            ).shouldBeEmpty()
        }
    }
}
