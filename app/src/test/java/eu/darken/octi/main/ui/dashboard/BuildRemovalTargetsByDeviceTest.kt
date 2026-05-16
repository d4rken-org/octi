package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncConnector
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Instant

class BuildRemovalTargetsByDeviceTest : BaseTest() {

    private val currentDeviceId = DeviceId("self")
    private val deviceA = DeviceId("device-a")
    private val deviceB = DeviceId("device-b")

    private val gdriveAlice = ConnectorId(ConnectorType.GDRIVE, "default", "alice@example.com")
    private val gdriveBob = ConnectorId(ConnectorType.GDRIVE, "default", "bob@example.com")
    private val gdriveAliceDup = ConnectorId(ConnectorType.GDRIVE, "alt", "different-account-uuid")
    private val octiserver = ConnectorId(ConnectorType.OCTISERVER, "default", "alice")

    private fun connector(id: ConnectorId, accountLabel: String): SyncConnector = mockk(relaxed = true) {
        every { identifier } returns id
        every { this@mockk.accountLabel } returns accountLabel
    }

    private fun metadata(deviceId: DeviceId) = DeviceMetadata(
        deviceId = deviceId,
        version = "1.0.0",
        platform = "android",
        label = "Phone",
        lastSeen = Instant.parse("2026-05-01T12:00:00Z"),
    )

    // Order by ConnectorType.ordinal — stable across the test, mirrors real contributions.
    private val displayOrder: (ConnectorType) -> Int = { it.ordinal }

    private fun call(
        activeConnectors: List<SyncConnector>,
        connectorMetadata: Map<ConnectorId, List<DeviceMetadata>>,
        pausedConnectorIds: Set<ConnectorId> = emptySet(),
        currentDeviceId: DeviceId = this.currentDeviceId,
        contributionDisplayOrder: (ConnectorType) -> Int = displayOrder,
    ) = DashboardVM.buildRemovalTargetsByDevice(
        activeConnectors = activeConnectors,
        connectorMetadata = connectorMetadata,
        pausedConnectorIds = pausedConnectorIds,
        currentDeviceId = currentDeviceId,
        contributionDisplayOrder = contributionDisplayOrder,
    )

    @Nested
    inner class `single connector` {
        @Test
        fun `device on one connector yields one non-paused target`() {
            val gdrive = connector(gdriveAlice, "alice@example.com")
            val result = call(
                activeConnectors = listOf(gdrive),
                connectorMetadata = mapOf(gdriveAlice to listOf(metadata(deviceA))),
            )

            val target = result.getValue(deviceA).also { it shouldHaveSize 1 }.single()
            target.connectorId shouldBe gdriveAlice
            target.type shouldBe ConnectorType.GDRIVE
            target.accountLabel shouldBe "alice@example.com"
            target.isPaused shouldBe false
        }
    }

    @Nested
    inner class `multi-connector disambiguation` {
        @Test
        fun `same-type connectors with identical account labels get disambiguated suffix`() {
            val first = connector(gdriveAlice, "alice@example.com")
            val second = connector(gdriveAliceDup, "alice@example.com")
            val result = call(
                activeConnectors = listOf(first, second),
                connectorMetadata = mapOf(
                    gdriveAlice to listOf(metadata(deviceA)),
                    gdriveAliceDup to listOf(metadata(deviceA)),
                ),
            )

            val targets = result.getValue(deviceA)
            targets shouldHaveSize 2
            // Both labels should be suffixed since they clash; neither equals the raw label.
            targets.forEach { it.accountLabel shouldBe "alice@example.com (${it.connectorId.account.take(8)})" }
        }

        @Test
        fun `distinct accounts retain their raw labels`() {
            val alice = connector(gdriveAlice, "alice@example.com")
            val bob = connector(gdriveBob, "bob@example.com")
            val result = call(
                activeConnectors = listOf(alice, bob),
                connectorMetadata = mapOf(
                    gdriveAlice to listOf(metadata(deviceA)),
                    gdriveBob to listOf(metadata(deviceA)),
                ),
            )

            val labels = result.getValue(deviceA).map { it.accountLabel }.toSet()
            labels shouldBe setOf("alice@example.com", "bob@example.com")
        }
    }

    @Nested
    inner class `paused flag` {
        @Test
        fun `paused connector target has isPaused true`() {
            val gdrive = connector(gdriveAlice, "alice@example.com")
            val server = connector(octiserver, "alice")
            val result = call(
                activeConnectors = listOf(gdrive, server),
                connectorMetadata = mapOf(
                    gdriveAlice to listOf(metadata(deviceA)),
                    octiserver to listOf(metadata(deviceA)),
                ),
                pausedConnectorIds = setOf(octiserver),
            )

            val byConnector = result.getValue(deviceA).associateBy { it.connectorId }
            byConnector.getValue(gdriveAlice).isPaused shouldBe false
            byConnector.getValue(octiserver).isPaused shouldBe true
        }
    }

    @Nested
    inner class `current device exclusion` {
        @Test
        fun `current device is omitted from the resulting map`() {
            val gdrive = connector(gdriveAlice, "alice@example.com")
            val result = call(
                activeConnectors = listOf(gdrive),
                connectorMetadata = mapOf(
                    gdriveAlice to listOf(metadata(currentDeviceId), metadata(deviceA)),
                ),
            )

            result shouldNotContainKey currentDeviceId
            result.getValue(deviceA) shouldHaveSize 1
        }
    }

    @Nested
    inner class `ordering` {
        @Test
        fun `targets sort by displayOrder then case-insensitive account label`() {
            val gdriveLow = connector(gdriveAlice, "alice@example.com")
            val gdriveHigh = connector(gdriveBob, "BOB@example.com")
            val server = connector(octiserver, "carol")
            val result = call(
                // displayOrder: GDRIVE=0 < OCTISERVER=1; account-label tie-break is case-insensitive.
                activeConnectors = listOf(server, gdriveHigh, gdriveLow),
                connectorMetadata = mapOf(
                    gdriveAlice to listOf(metadata(deviceA)),
                    gdriveBob to listOf(metadata(deviceA)),
                    octiserver to listOf(metadata(deviceA)),
                ),
                contributionDisplayOrder = { when (it) {
                    ConnectorType.GDRIVE -> 0
                    ConnectorType.OCTISERVER -> 1
                } },
            )

            val ordered = result.getValue(deviceA).map { it.connectorId }
            ordered shouldBe listOf(gdriveAlice, gdriveBob, octiserver)
        }

        @Test
        fun `unknown connector types land last via Int MAX_VALUE`() {
            val gdrive = connector(gdriveAlice, "alice@example.com")
            val server = connector(octiserver, "alice")
            val result = call(
                activeConnectors = listOf(gdrive, server),
                connectorMetadata = mapOf(
                    gdriveAlice to listOf(metadata(deviceA)),
                    octiserver to listOf(metadata(deviceA)),
                ),
                // Only GDRIVE known; OCTISERVER falls through to MAX_VALUE.
                contributionDisplayOrder = { type -> if (type == ConnectorType.GDRIVE) 0 else Int.MAX_VALUE },
            )

            val ordered = result.getValue(deviceA).map { it.connectorId }
            ordered shouldBe listOf(gdriveAlice, octiserver)
        }
    }

    @Nested
    inner class `degenerate inputs` {
        @Test
        fun `empty connector metadata yields empty map`() {
            val result = call(activeConnectors = emptyList(), connectorMetadata = emptyMap())
            result.isEmpty() shouldBe true
        }

        @Test
        fun `connector with empty metadata is skipped`() {
            val gdrive = connector(gdriveAlice, "alice@example.com")
            val result = call(
                activeConnectors = listOf(gdrive),
                connectorMetadata = mapOf(gdriveAlice to emptyList()),
            )
            result.isEmpty() shouldBe true
        }

        @Test
        fun `metadata for connector missing from activeConnectors still produces target with empty account label`() {
            // Defensive: should not happen in production (connector list and metadata are derived together),
            // but the function must not crash on a missing label lookup.
            val result = call(
                activeConnectors = emptyList(),
                connectorMetadata = mapOf(gdriveAlice to listOf(metadata(deviceB))),
            )
            result.getValue(deviceB).single().accountLabel shouldBe ""
        }
    }
}
