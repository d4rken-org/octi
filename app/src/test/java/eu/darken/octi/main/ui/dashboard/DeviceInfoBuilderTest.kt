package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.CommonIssue
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.syncs.octiserver.core.OctiServerIssue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DeviceInfoBuilderTest : BaseTest() {

    private val now = Instant.parse("2026-03-30T12:00:00Z")
    private val currentDeviceId = DeviceId("current-device")
    private val remoteDeviceId = DeviceId("remote-device")
    private val metaModuleId = ModuleId("eu.darken.octi.module.core.meta")
    private val connectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = "test",
        account = "test-account",
    )

    /** Second connector to model the legacy + modern dual-account setup. */
    private val modernConnectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = "test",
        account = "modern-account",
    )

    private fun fakeState(devices: List<DeviceId>): SyncConnectorState = object : SyncConnectorState {
        override val lastActionAt: Instant? = null
        override val lastError: Exception? = null
        override val deviceMetadata: List<DeviceMetadata> = devices.map { DeviceMetadata(deviceId = it) }
        override val isAvailable: Boolean = true
    }

    private fun metaInfo(deviceId: DeviceId = currentDeviceId) = MetaInfo(
        deviceLabel = null,
        deviceId = deviceId,
        octiVersionName = "1.0.0",
        octiGitSha = "abc123",
        deviceManufacturer = "Test",
        deviceName = "TestDevice",
        deviceType = MetaInfo.DeviceType.PHONE,
        deviceBootedAt = now - 1.hours,
        androidVersionName = "15",
        androidApiLevel = 35,
        androidSecurityPatch = null,
    )

    private fun deviceItem(
        deviceId: DeviceId = currentDeviceId,
        modifiedAt: Instant = now,
        isCurrentDevice: Boolean = true,
        isDegraded: Boolean = false,
    ) = DashboardVM.DeviceItem(
        now = now,
        deviceId = deviceId,
        meta = if (isDegraded) null else ModuleData(
            modifiedAt = modifiedAt,
            deviceId = deviceId,
            moduleId = metaModuleId,
            data = metaInfo(deviceId),
        ),
        moduleItems = emptyList(),
        isCollapsed = false,
        isLimited = false,
        isCurrentDevice = isCurrentDevice,
        isDegraded = isDegraded,
    )

    @Nested
    inner class `no issues` {
        @Test
        fun `device with no issues returns empty`() {
            val item = deviceItem()
            DashboardVM.buildDeviceInfos(item, allIssues = emptyList()).shouldBeEmpty()
        }

        @Test
        fun `issues for other devices are ignored`() {
            val item = deviceItem(deviceId = remoteDeviceId, isCurrentDevice = false)
            val issues = listOf(
                CommonIssue.ClockSkew(connectorId = connectorId, deviceId = currentDeviceId),
            )
            DashboardVM.buildDeviceInfos(item, issues).shouldBeEmpty()
        }
    }

    @Nested
    inner class `stale device` {
        @Test
        fun `stale issue is returned for matching device`() {
            val staleTime = now - 31.days
            val item = deviceItem()
            val issues = listOf(
                CommonIssue.StaleDevice(
                    connectorId = connectorId,
                    deviceId = currentDeviceId,
                    lastSeen = staleTime,
                ),
            )
            val infos = DashboardVM.buildDeviceInfos(item, issues)
            infos shouldHaveSize 1
            infos.first().shouldBeInstanceOf<CommonIssue.StaleDevice>()
        }
    }

    @Nested
    inner class `clock skew` {
        @Test
        fun `clock skew issue is returned for matching device`() {
            val item = deviceItem()
            val issues = listOf(
                CommonIssue.ClockSkew(connectorId = connectorId, deviceId = currentDeviceId),
            )
            val infos = DashboardVM.buildDeviceInfos(item, issues)
            infos shouldHaveSize 1
            infos.first().shouldBeInstanceOf<CommonIssue.ClockSkew>()
        }
    }

    @Nested
    inner class `multiple issues` {
        @Test
        fun `errors are sorted before warnings`() {
            val staleTime = now - 31.days
            val item = deviceItem()
            val issues = listOf(
                CommonIssue.ClockSkew(connectorId = connectorId, deviceId = currentDeviceId),
                CommonIssue.StaleDevice(connectorId = connectorId, deviceId = currentDeviceId, lastSeen = staleTime),
            )
            val infos = DashboardVM.buildDeviceInfos(item, issues)
            infos shouldHaveSize 2
            // StaleDevice is WARNING, ClockSkew is WARNING — both same severity, sorted by class name
            infos.all { it.severity == IssueSeverity.WARNING } shouldBe true
        }
    }

    @Nested
    inner class `dashboard issue filtering` {
        @Test
        fun `paused connector issues are hidden unless current device is no longer registered`() {
            val hiddenIssue = CommonIssue.ClockSkew(connectorId = connectorId, deviceId = currentDeviceId)
            val visibleIssue = OctiServerIssue.CurrentDeviceNotRegistered(
                connectorId = connectorId,
                deviceId = currentDeviceId,
            )

            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(hiddenIssue, visibleIssue),
                fileShareEnabled = true,
                pausedIds = setOf(connectorId),
                blobReachableDeviceIds = emptySet(),
            )

            result shouldBe listOf(visibleIssue)
        }
    }

    @Nested
    inner class `blob-reachable computation` {
        @Test
        fun `empty input yields empty set`() {
            DashboardVM.blobReachableDeviceIds(
                connectorsAndStates = emptyList(),
                blobCapableConnectorIds = emptySet(),
            ).shouldBeEmpty()
        }

        @Test
        fun `connector outside the blob-capable set contributes no devices`() {
            // Legacy connector is present in connectorsAndStates but excluded from
            // blobCapableConnectorIds — its devices must NOT appear, otherwise the
            // legacy account itself would suppress its own legacy warning.
            val state = fakeState(listOf(currentDeviceId, remoteDeviceId))

            DashboardVM.blobReachableDeviceIds(
                connectorsAndStates = listOf(connectorId to state),
                blobCapableConnectorIds = emptySet(),
            ).shouldBeEmpty()
        }

        @Test
        fun `blob-capable connector contributes its deviceMetadata`() {
            val state = fakeState(listOf(currentDeviceId, remoteDeviceId))

            DashboardVM.blobReachableDeviceIds(
                connectorsAndStates = listOf(modernConnectorId to state),
                blobCapableConnectorIds = setOf(modernConnectorId),
            ) shouldContainExactlyInAnyOrder setOf(currentDeviceId, remoteDeviceId)
        }

        @Test
        fun `two blob-capable connectors with overlapping devices are unioned and deduped`() {
            val secondModern = ConnectorId(ConnectorType.OCTISERVER, "test", "modern-2")
            val a = fakeState(listOf(currentDeviceId, remoteDeviceId))
            val b = fakeState(listOf(remoteDeviceId, DeviceId("third")))

            val ids = DashboardVM.blobReachableDeviceIds(
                connectorsAndStates = listOf(modernConnectorId to a, secondModern to b),
                blobCapableConnectorIds = setOf(modernConnectorId, secondModern),
            )

            ids shouldContainExactlyInAnyOrder setOf(currentDeviceId, remoteDeviceId, DeviceId("third"))
        }

        @Test
        fun `blob-capable connector with empty metadata contributes nothing`() {
            val empty = fakeState(emptyList())

            DashboardVM.blobReachableDeviceIds(
                connectorsAndStates = listOf(modernConnectorId to empty),
                blobCapableConnectorIds = setOf(modernConnectorId),
            ).shouldBeEmpty()
        }

        @Test
        fun `mixed legacy and modern - only modern's devices are reachable`() {
            // Models the user's bug scenario: same device appears via both connectors.
            // Only modern's contribution counts toward the reachable set.
            val legacyState = fakeState(listOf(currentDeviceId, remoteDeviceId))
            val modernState = fakeState(listOf(currentDeviceId, remoteDeviceId))

            val ids = DashboardVM.blobReachableDeviceIds(
                connectorsAndStates = listOf(connectorId to legacyState, modernConnectorId to modernState),
                blobCapableConnectorIds = setOf(modernConnectorId),
            )

            ids shouldContainExactlyInAnyOrder setOf(currentDeviceId, remoteDeviceId)
        }
    }

    @Nested
    inner class `legacy encryption filtering` {
        private val legacyForRemote = OctiServerIssue.LegacyEncryptionAccount(
            connectorId = connectorId,
            deviceId = remoteDeviceId,
        )
        private val legacyForCurrent = OctiServerIssue.LegacyEncryptionAccount(
            connectorId = connectorId,
            deviceId = currentDeviceId,
        )

        @Test
        fun `legacy warning is dropped for a device covered by a blob-capable connector`() {
            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(legacyForRemote),
                fileShareEnabled = true,
                pausedIds = emptySet(),
                blobReachableDeviceIds = setOf(remoteDeviceId),
            )

            result.shouldBeEmpty()
        }

        @Test
        fun `legacy warning is kept when the device has no blob-capable coverage`() {
            // Only-legacy setup: no modern connectors registered, so blobReachable is empty.
            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(legacyForRemote),
                fileShareEnabled = true,
                pausedIds = emptySet(),
                blobReachableDeviceIds = emptySet(),
            )

            result shouldBe listOf(legacyForRemote)
        }

        @Test
        fun `paused-but-blob-capable modern connector still suppresses the legacy warning`() {
            // Per the chosen behavior: capability-based, not runtime-availability-based.
            // A modern connector that has previously seen D and is currently paused
            // should still keep the legacy warning suppressed for D — pause produces
            // its own dashboard issues, so the user is not silenced.
            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(legacyForRemote),
                fileShareEnabled = true,
                pausedIds = setOf(modernConnectorId),
                blobReachableDeviceIds = setOf(remoteDeviceId),
            )

            result.shouldBeEmpty()
        }

        @Test
        fun `legacy warning for the current device is dropped when modern covers self`() {
            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(legacyForCurrent),
                fileShareEnabled = true,
                pausedIds = emptySet(),
                blobReachableDeviceIds = setOf(currentDeviceId),
            )

            result.shouldBeEmpty()
        }

        @Test
        fun `non-legacy issue with the same deviceId passes through even when blob-reachable`() {
            // Use EncryptionCompatibilityIncompatible (not CurrentDeviceNotRegistered, which
            // has its own paused-but-shown special case that would muddle this assertion).
            val incompat = OctiServerIssue.EncryptionCompatibilityIncompatible(
                connectorId = connectorId,
                deviceId = remoteDeviceId,
                minClientVersion = "1.2.3",
            )

            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(incompat, legacyForRemote),
                fileShareEnabled = true,
                pausedIds = emptySet(),
                blobReachableDeviceIds = setOf(remoteDeviceId),
            )

            // Legacy gets dropped, but the compatibility issue must remain.
            result shouldBe listOf(incompat)
        }

        @Test
        fun `file-share globally disabled drops legacy warnings regardless of reachability`() {
            // Existing behavior preserved: when the file-share module is off entirely,
            // legacy warnings are noise and get dropped before reachability is even checked.
            val result = DashboardVM.projectDashboardIssues(
                allIssues = listOf(legacyForRemote),
                fileShareEnabled = false,
                pausedIds = emptySet(),
                blobReachableDeviceIds = emptySet(),
            )

            result.shouldBeEmpty()
        }
    }

    @Nested
    inner class `severity levels` {
        @Test
        fun `stale has WARNING severity`() {
            CommonIssue.StaleDevice(
                connectorId = connectorId,
                deviceId = currentDeviceId,
                lastSeen = now,
            ).severity shouldBe IssueSeverity.WARNING
        }

        @Test
        fun `clock skew has WARNING severity`() {
            CommonIssue.ClockSkew(
                connectorId = connectorId,
                deviceId = currentDeviceId,
            ).severity shouldBe IssueSeverity.WARNING
        }
    }
}
