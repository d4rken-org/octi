package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class BuildDegradedDeviceItemsTest : BaseTest() {

    private val now = Instant.parse("2026-05-05T12:00:00Z")
    private val gracePeriod: Duration = 5.minutes
    private val currentDeviceId = DeviceId("self")
    private val deviceA = DeviceId("device-a")
    private val deviceB = DeviceId("device-b")

    private val gdrive = ConnectorId(ConnectorType.GDRIVE, "default", "alice@example.com")
    private val octiserver = ConnectorId(ConnectorType.OCTISERVER, "default", "alice")

    private fun metadata(
        deviceId: DeviceId,
        label: String? = "Phone",
        platform: String? = "android",
        version: String? = "1.0.0",
        lastSeen: Instant? = now - 1.minutes,
        addedAt: Instant? = now - 1.hours,
    ) = DeviceMetadata(
        deviceId = deviceId,
        version = version,
        platform = platform,
        label = label,
        lastSeen = lastSeen,
        addedAt = addedAt,
    )

    private fun call(
        connectorMetadata: Map<ConnectorId, List<DeviceMetadata>>,
        normalDeviceIds: Set<DeviceId> = emptySet(),
    ) = DashboardVM.buildDegradedDeviceItems(
        now = now,
        connectorMetadata = connectorMetadata,
        normalDeviceIds = normalDeviceIds,
        currentDeviceId = currentDeviceId,
        gracePeriod = gracePeriod,
    )

    @Nested
    inner class `cross-connector deduplication` {
        @Test
        fun `same deviceId reported by two connectors yields a single item`() {
            // Regression: previously crashed LazyVerticalGrid with duplicate key.
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA)),
                    octiserver to listOf(metadata(deviceA)),
                ),
            )

            items shouldHaveSize 1
            items.single().deviceId shouldBe deviceA
        }

        @Test
        fun `distinct devices across connectors are kept separately`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA)),
                    octiserver to listOf(metadata(deviceB)),
                ),
            )

            items shouldHaveSize 2
            items.map { it.deviceId }.toSet() shouldBe setOf(deviceA, deviceB)
        }
    }

    @Nested
    inner class `selection rule` {
        @Test
        fun `non-null label wins over null label even when null-label has newer lastSeen`() {
            val itemsLabelWins = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, label = null, lastSeen = now)),
                    octiserver to listOf(metadata(deviceA, label = "RealLabel", lastSeen = now - 10.minutes)),
                ),
            )

            itemsLabelWins.single().degradedLabel shouldBe "RealLabel"
            itemsLabelWins.single().degradedConnectorId shouldBe octiserver
            // degradedLastSeen mirrors the winner — not the absolute max across connectors.
            itemsLabelWins.single().degradedLastSeen shouldBe (now - 10.minutes)
        }

        @Test
        fun `newer lastSeen wins when both have non-null label`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, label = "OldName", lastSeen = now - 10.minutes)),
                    octiserver to listOf(metadata(deviceA, label = "NewName", lastSeen = now - 1.minutes)),
                ),
            )

            items.single().degradedLabel shouldBe "NewName"
            items.single().degradedConnectorId shouldBe octiserver
            items.single().degradedLastSeen shouldBe (now - 1.minutes)
        }

        @Test
        fun `equal lastSeen falls back to stable connectorId idString`() {
            // gdrive.idString = "gdrive-default-alice@example.com",
            // octiserver.idString = "kserver-default-alice".
            // "gdrive…" < "kserver…" alphabetically, so gdrive wins.
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                    octiserver to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                ),
            )

            items.single().degradedConnectorId shouldBe gdrive

            // Running again (different map iteration order) yields the same winner.
            val items2 = call(
                mapOf(
                    octiserver to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                    gdrive to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                ),
            )
            items2.single().degradedConnectorId shouldBe gdrive
        }

        @Test
        fun `both lastSeen null still resolves deterministically`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, lastSeen = null)),
                    octiserver to listOf(metadata(deviceA, lastSeen = null)),
                ),
            )

            items.single().degradedConnectorId shouldBe gdrive
            items.single().degradedLastSeen shouldBe null
        }
    }

    @Nested
    inner class `filter preservation` {
        @Test
        fun `device present in normalDeviceIds is excluded`() {
            val items = call(
                connectorMetadata = mapOf(gdrive to listOf(metadata(deviceA))),
                normalDeviceIds = setOf(deviceA),
            )

            items.shouldBeEmpty()
        }

        @Test
        fun `current device is never reported as degraded`() {
            val items = call(
                mapOf(gdrive to listOf(metadata(currentDeviceId))),
            )

            items.shouldBeEmpty()
        }

        @Test
        fun `device added within grace period is filtered out`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, addedAt = now - 1.minutes)),
                ),
            )

            items.shouldBeEmpty()
        }

        @Test
        fun `device added before grace period is included`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, addedAt = now - 6.minutes)),
                ),
            )

            items shouldHaveSize 1
        }

        @Test
        fun `device with null addedAt is included`() {
            // Matches the existing `?.let { … } != false` semantics: null grace check passes.
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA, addedAt = null))),
            )

            items shouldHaveSize 1
        }
    }

    @Nested
    inner class `degenerate inputs` {
        @Test
        fun `empty connector map yields empty list`() {
            call(connectorMetadata = emptyMap()).shouldBeEmpty()
        }

        @Test
        fun `connector with empty metadata yields empty list`() {
            call(mapOf(gdrive to emptyList())).shouldBeEmpty()
        }
    }

    @Nested
    inner class `result shape` {
        @Test
        fun `degraded item has expected fixed fields`() {
            val items = call(mapOf(gdrive to listOf(metadata(deviceA))))

            val item = items.single()
            item.isDegraded shouldBe true
            item.isCurrentDevice shouldBe false
            item.isCollapsed shouldBe false
            item.isLimited shouldBe false
            item.meta shouldBe null
            item.moduleItems.shouldBeEmpty()
            item.degradedConnectorId shouldBe gdrive
            item.degradedLabel shouldBe "Phone"
            item.degradedPlatform shouldBe "android"
            item.degradedVersion shouldBe "1.0.0"
        }
    }
}

