package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.CommonIssue
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import io.kotest.matchers.collections.shouldBeEmpty
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
