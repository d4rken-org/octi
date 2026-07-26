package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.main.ui.dashboard.DashboardVM.PlaceholderData.Kind
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BuildPlaceholderDeviceItemsTest : BaseTest() {

    private val now = Instant.parse("2026-05-05T12:00:00Z")
    private val gracePeriod: Duration = 5.minutes
    private val settleWindow: Duration = 3.seconds
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

    /**
     * Defaults model the settled case: every connector completed a full read long ago and is
     * idle, i.e. a missing payload is genuine degradation.
     */
    private fun call(
        connectorMetadata: Map<ConnectorId, List<DeviceMetadata>>,
        normalDeviceIds: Set<DeviceId> = emptySet(),
        lastFullReadAt: Map<ConnectorId, Instant?> = connectorMetadata.keys.associateWith { now - 1.hours },
        readingConnectorIds: Set<ConnectorId> = emptySet(),
    ) = DashboardVM.buildPlaceholderDeviceItems(
        now = now,
        connectorMetadata = connectorMetadata,
        lastFullReadAt = lastFullReadAt,
        readingConnectorIds = readingConnectorIds,
        normalDeviceIds = normalDeviceIds,
        currentDeviceId = currentDeviceId,
        gracePeriod = gracePeriod,
        settleWindow = settleWindow,
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

            val placeholder = itemsLabelWins.single().placeholder!!
            placeholder.metadata.label shouldBe "RealLabel"
            placeholder.connectorId shouldBe octiserver
            // lastSeen mirrors the winner — not the absolute max across connectors.
            placeholder.metadata.lastSeen shouldBe (now - 10.minutes)
        }

        @Test
        fun `newer lastSeen wins when both have non-null label`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, label = "OldName", lastSeen = now - 10.minutes)),
                    octiserver to listOf(metadata(deviceA, label = "NewName", lastSeen = now - 1.minutes)),
                ),
            )

            val placeholder = items.single().placeholder!!
            placeholder.metadata.label shouldBe "NewName"
            placeholder.connectorId shouldBe octiserver
            placeholder.metadata.lastSeen shouldBe (now - 1.minutes)
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

            items.single().placeholder!!.connectorId shouldBe gdrive

            // Running again (different map iteration order) yields the same winner.
            val items2 = call(
                mapOf(
                    octiserver to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                    gdrive to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                ),
            )
            items2.single().placeholder!!.connectorId shouldBe gdrive
        }

        @Test
        fun `both lastSeen null still resolves deterministically`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, lastSeen = null)),
                    octiserver to listOf(metadata(deviceA, lastSeen = null)),
                ),
            )

            items.single().placeholder!!.connectorId shouldBe gdrive
            items.single().placeholder!!.metadata.lastSeen shouldBe null
        }

        @Test
        fun `tie-break ordering holds for syncing and unverified kinds too`() {
            val syncing = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                    octiserver to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                ),
                lastFullReadAt = mapOf(gdrive to null, octiserver to null),
                readingConnectorIds = setOf(gdrive, octiserver),
            )
            syncing.single().placeholder!!.kind shouldBe Kind.SYNCING
            syncing.single().placeholder!!.connectorId shouldBe gdrive

            val unverified = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                    octiserver to listOf(metadata(deviceA, lastSeen = now - 5.minutes)),
                ),
                lastFullReadAt = mapOf(gdrive to null, octiserver to null),
            )
            unverified.single().placeholder!!.kind shouldBe Kind.UNVERIFIED
            unverified.single().placeholder!!.connectorId shouldBe gdrive
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
        fun `current device is never reported as a placeholder`() {
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
            item.isPlaceholder shouldBe true
            item.isSyncing shouldBe false
            item.isUnverified shouldBe false
            item.isCurrentDevice shouldBe false
            item.isCollapsed shouldBe false
            item.isLimited shouldBe false
            item.meta shouldBe null
            item.moduleItems.shouldBeEmpty()
            item.placeholder!!.kind shouldBe Kind.DEGRADED
            item.placeholder!!.connectorId shouldBe gdrive
            item.placeholder!!.metadata.label shouldBe "Phone"
            item.placeholder!!.metadata.platform shouldBe "android"
            item.placeholder!!.metadata.version shouldBe "1.0.0"
        }
    }

    @Nested
    inner class `classification by read coverage` {
        @Test
        fun `never read and currently reading is SYNCING regardless of addedAt`() {
            listOf(now - 1.hours, now - 1.minutes, null).forEach { addedAt ->
                val items = call(
                    mapOf(gdrive to listOf(metadata(deviceA, addedAt = addedAt))),
                    lastFullReadAt = mapOf(gdrive to null),
                    readingConnectorIds = setOf(gdrive),
                )

                items.single().placeholder!!.kind shouldBe Kind.SYNCING
                items.single().isDegraded shouldBe false
            }
        }

        @Test
        fun `never read and idle is UNVERIFIED`() {
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA))),
                lastFullReadAt = mapOf(gdrive to null),
            )

            items.single().placeholder!!.kind shouldBe Kind.UNVERIFIED
            items.single().isDegraded shouldBe false
        }

        @Test
        fun `read completed within the settle window is SYNCING`() {
            // The ingestion-race guard: the connector published its snapshot, but the module
            // repos haven't caught up yet.
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA))),
                lastFullReadAt = mapOf(gdrive to now - 1.seconds),
            )

            items.single().placeholder!!.kind shouldBe Kind.SYNCING
        }

        @Test
        fun `read completed outside the settle window is DEGRADED`() {
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA))),
                lastFullReadAt = mapOf(gdrive to now - 10.seconds),
            )

            items.single().placeholder!!.kind shouldBe Kind.DEGRADED
        }

        @Test
        fun `settled read plus addedAt within grace omits the device`() {
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA, addedAt = now - 1.minutes))),
                lastFullReadAt = mapOf(gdrive to now - 10.seconds),
            )

            items.shouldBeEmpty()
        }

        @Test
        fun `settled read plus null addedAt is DEGRADED`() {
            // Documents the retained sharp edge: unknown add time counts as out-of-grace.
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA, addedAt = null))),
                lastFullReadAt = mapOf(gdrive to now - 10.seconds),
            )

            items.single().placeholder!!.kind shouldBe Kind.DEGRADED
        }

        @Test
        fun `grace period does not suppress a syncing device`() {
            val items = call(
                mapOf(gdrive to listOf(metadata(deviceA, addedAt = now - 1.minutes))),
                lastFullReadAt = mapOf(gdrive to null),
                readingConnectorIds = setOf(gdrive),
            )

            items.single().placeholder!!.kind shouldBe Kind.SYNCING
        }
    }

    @Nested
    inner class `most-optimistic-wins across holders` {
        @Test
        fun `one holder still reading outranks a settled holder`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, label = "FromGdrive")),
                    octiserver to listOf(metadata(deviceA, label = "FromServer")),
                ),
                lastFullReadAt = mapOf(gdrive to now - 1.hours, octiserver to null),
                readingConnectorIds = setOf(octiserver),
            )

            val placeholder = items.single().placeholder!!
            placeholder.kind shouldBe Kind.SYNCING
            placeholder.connectorId shouldBe octiserver
            placeholder.metadata.label shouldBe "FromServer"
        }

        @Test
        fun `one never-read idle holder outranks a settled holder`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA, label = "FromGdrive")),
                    octiserver to listOf(metadata(deviceA, label = "FromServer")),
                ),
                lastFullReadAt = mapOf(gdrive to now - 1.hours, octiserver to null),
            )

            val placeholder = items.single().placeholder!!
            placeholder.kind shouldBe Kind.UNVERIFIED
            placeholder.connectorId shouldBe octiserver
            placeholder.metadata.label shouldBe "FromServer"
        }

        @Test
        fun `all holders settled yields DEGRADED`() {
            val items = call(
                mapOf(
                    gdrive to listOf(metadata(deviceA)),
                    octiserver to listOf(metadata(deviceA)),
                ),
                lastFullReadAt = mapOf(gdrive to now - 1.hours, octiserver to now - 10.seconds),
            )

            items.single().placeholder!!.kind shouldBe Kind.DEGRADED
        }
    }

    /**
     * The bug this classification exists for: after linking a new client the peer list is visible
     * ~1 RTT in, but the payload read takes ~10-20s. Replays that timeline through the placeholder
     * projection and asserts no red card appears at any point. Tested at the projection level
     * rather than by wiring a full DashboardVM under test.
     */
    @Nested
    inner class `ingestion lag emission sequence` {
        @Test
        fun `no DEGRADED item is ever emitted while the first read is in flight`() {
            val readStart = now
            val readDone = readStart + 12.seconds
            val meta = metadata(deviceA, addedAt = readStart - 3.hours)

            fun project(
                at: Instant,
                readAt: Instant?,
                reading: Boolean,
                normalDeviceIds: Set<DeviceId>,
            ) = DashboardVM.buildPlaceholderDeviceItems(
                now = at,
                connectorMetadata = mapOf(gdrive to listOf(meta)),
                lastFullReadAt = mapOf(gdrive to readAt),
                readingConnectorIds = if (reading) setOf(gdrive) else emptySet(),
                normalDeviceIds = normalDeviceIds,
                currentDeviceId = currentDeviceId,
                gracePeriod = gracePeriod,
                settleWindow = settleWindow,
            )

            // 1) Device list arrived, payload read still running.
            val whileReading = project(
                at = readStart + 1.seconds,
                readAt = null,
                reading = true,
                normalDeviceIds = emptySet(),
            )
            whileReading.single().placeholder!!.kind shouldBe Kind.SYNCING

            // 2) Read finished, but byDevice hasn't ingested the payload yet.
            val duringIngestion = project(
                at = readDone + 1.seconds,
                readAt = readDone,
                reading = false,
                normalDeviceIds = emptySet(),
            )
            duringIngestion.single().placeholder!!.kind shouldBe Kind.SYNCING

            // 3) Payload ingested — the device is a normal item now, no placeholder at all.
            val afterIngestion = project(
                at = readDone + 2.seconds,
                readAt = readDone,
                reading = false,
                normalDeviceIds = setOf(deviceA),
            )
            afterIngestion.shouldBeEmpty()

            listOf(whileReading, duringIngestion, afterIngestion).forEach { emission ->
                emission.none { it.isDegraded } shouldBe true
            }
        }
    }
}
