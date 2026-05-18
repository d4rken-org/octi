package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.Capability
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.encryption.EncryptionMode
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
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

    private val fullCapabilities = setOf(
        Capability.ENCRYPTION_NAMESPACE_REPORTED,
        Capability.encryption(EncryptionMode.AES256_SIV),
        Capability.encryption(EncryptionMode.AES256_GCM_SIV),
    )
    private val sivOnlyCapabilities = setOf(
        Capability.ENCRYPTION_NAMESPACE_REPORTED,
        Capability.encryption(EncryptionMode.AES256_SIV),
    )

    private fun peer(
        id: String = "peer",
        version: String? = null,
        platform: String? = "android",
        addedAt: kotlin.time.Instant? = now - 1.minutes,
        capabilities: Set<String>? = null,
    ) = DeviceMetadata(
        deviceId = DeviceId(id),
        version = version,
        platform = platform,
        addedAt = addedAt,
        capabilities = capabilities,
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
                OctiServerIssue.EncryptionCompatibilityIncompatible.AndroidClientTooOld(
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
        fun `SIV account does not flag Android peer (SIV is always supported on Android)`() {
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

        // --- capability-based path tests ---

        @Test
        fun `peer with full capabilities is never flagged on GCM account, any platform`() {
            val androidPeer = peer(version = "0.5.0", platform = "android", capabilities = fullCapabilities)
            val webPeer = peer(id = "web-peer", version = "octi-web/0.0.0", platform = "web", capabilities = fullCapabilities)
            OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(androidPeer, webPeer),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            ).shouldBeEmpty()
        }

        @Test
        fun `web peer with only SIV capability is flagged as UnsupportedEncryptionMode on GCM account`() {
            val p = peer(version = "1.0.0", platform = "web", capabilities = sivOnlyCapabilities)
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
                OctiServerIssue.EncryptionCompatibilityIncompatible.UnsupportedEncryptionMode(
                    connectorId = connectorId,
                    deviceId = p.deviceId,
                    requiredMode = EncryptionMode.AES256_GCM_SIV,
                ),
            )
        }

        @Test
        fun `peer with only namespace marker (empty value tags) is flagged as UnsupportedEncryptionMode`() {
            val p = peer(
                version = "1.0.0",
                platform = "web",
                capabilities = setOf(Capability.ENCRYPTION_NAMESPACE_REPORTED),
            )
            val issues = OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = gcmSivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            )
            issues.first().shouldBeInstanceOf<OctiServerIssue.EncryptionCompatibilityIncompatible.UnsupportedEncryptionMode>()
        }

        @Test
        fun `peer reporting tags only in unrelated namespaces is not falsely flagged`() {
            // Per-namespace authority: peer publishes future:foo but doesn't speak the encryption
            // namespace. Encryption support is therefore "unknown" → falls back to per-peer heuristic.
            val p = peer(
                version = null,
                platform = "web",
                capabilities = setOf("clipboard:rich"),
            )
            // Non-Android peer with unknown encryption + null version → no flag.
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
        fun `Android peer with old version but explicit GCM capability is not flagged`() {
            // Capabilities win over version when both are known. Useful if a forked / dev build
            // reports an old version but actually supports GCM-SIV.
            val p = peer(version = "0.5.0", platform = "android", capabilities = fullCapabilities)
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
        fun `SIV account flags peer explicitly lacking SIV (no early-return on SIV)`() {
            // Previously the SIV-keyset path short-circuited via expectedMinClientVersion returning null.
            // With the refactor, capabilities run on SIV too — a (hypothetical) peer reporting it
            // doesn't support SIV is correctly flagged.
            val sivLackingCapabilities = setOf(
                Capability.ENCRYPTION_NAMESPACE_REPORTED,
                Capability.encryption(EncryptionMode.AES256_GCM_SIV),
                // Note: AES256_SIV intentionally absent.
            )
            val p = peer(version = "1.0.0", platform = "web", capabilities = sivLackingCapabilities)
            val issues = OctiServerEncryptionIssues.buildEncryptionCompatibilityIssues(
                connectorId = connectorId,
                ownDeviceId = ownDeviceId,
                keyset = sivKeyset,
                metadata = listOf(p),
                dataDeviceIds = emptySet(),
                now = now,
                gracePeriod = gracePeriod,
            )
            issues shouldContainExactly listOf(
                OctiServerIssue.EncryptionCompatibilityIncompatible.UnsupportedEncryptionMode(
                    connectorId = connectorId,
                    deviceId = p.deviceId,
                    requiredMode = EncryptionMode.AES256_SIV,
                ),
            )
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
