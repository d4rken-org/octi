package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncConnectorState
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DeviceLastSeenTest : BaseTest() {

    private val now = Instant.parse("2026-05-05T12:00:00Z")
    private val deviceA = DeviceId("device-a")
    private val deviceB = DeviceId("device-b")

    private fun metadata(deviceId: DeviceId, lastSeen: Instant?) = DeviceMetadata(
        deviceId = deviceId,
        version = "1.0.0",
        platform = "android",
        label = "Phone",
        lastSeen = lastSeen,
        addedAt = now - 1.hours,
    )

    private fun state(vararg metadata: DeviceMetadata): SyncConnectorState = object : SyncConnectorState {
        override val lastActionAt: Instant? = now
        override val lastError: Exception? = null
        override val deviceMetadata: List<DeviceMetadata> = metadata.toList()
        override val isAvailable: Boolean = true
    }

    private fun metaModule(modifiedAt: Instant) = ModuleData(
        modifiedAt = modifiedAt,
        deviceId = deviceA,
        moduleId = ModuleId("meta"),
        data = MetaInfo(
            deviceLabel = "Phone",
            deviceId = deviceA,
            octiVersionName = "1.0.0",
            octiGitSha = "abc1234",
            deviceManufacturer = "Google",
            deviceName = "Phone",
            deviceType = MetaInfo.DeviceType.PHONE,
        ),
    )

    private fun deviceItem(
        lastSeen: Instant?,
        metaModifiedAt: Instant?,
    ) = DashboardVM.DeviceItem(
        now = now,
        deviceId = deviceA,
        meta = metaModifiedAt?.let { metaModule(it) },
        moduleItems = emptyList(),
        isCollapsed = false,
        isLimited = false,
        isCurrentDevice = false,
        lastSeen = lastSeen,
    )

    @Nested
    inner class `lastSeen aggregation` {
        @Test
        fun `no states yields no sightings`() {
            DashboardVM.lastSeenByDevice(emptyList()).shouldBeEmpty()
        }

        @Test
        fun `the newest sighting across connectors wins`() {
            val states = listOf(
                state(metadata(deviceA, now - 30.minutes)),
                state(metadata(deviceA, now - 2.minutes)),
                state(metadata(deviceA, now - 10.minutes)),
            )

            DashboardVM.lastSeenByDevice(states) shouldBe mapOf(deviceA to now - 2.minutes)
        }

        @Test
        fun `devices are tracked independently`() {
            val states = listOf(
                state(metadata(deviceA, now - 30.minutes), metadata(deviceB, now - 5.minutes)),
                state(metadata(deviceA, now - 1.minutes)),
            )

            DashboardVM.lastSeenByDevice(states) shouldBe mapOf(
                deviceA to now - 1.minutes,
                deviceB to now - 5.minutes,
            )
        }

        @Test
        fun `a device without a sighting is absent`() {
            val states = listOf(state(metadata(deviceA, null), metadata(deviceB, now - 5.minutes)))

            DashboardVM.lastSeenByDevice(states) shouldBe mapOf(deviceB to now - 5.minutes)
        }
    }

    @Nested
    inner class `card timestamp` {
        @Test
        fun `a fresher sighting than the last write wins`() {
            // Self-device on a write-only run: the module data is old, the sighting is not.
            deviceItem(
                lastSeen = now - 1.minutes,
                metaModifiedAt = now - 3.hours,
            ).lastActivityAt shouldBe now - 1.minutes
        }

        @Test
        fun `a sighting older than the last write does not move the card backwards`() {
            deviceItem(
                lastSeen = now - 3.hours,
                metaModifiedAt = now - 1.minutes,
            ).lastActivityAt shouldBe now - 1.minutes
        }

        @Test
        fun `no sighting falls back to the last write`() {
            deviceItem(
                lastSeen = null,
                metaModifiedAt = now - 20.minutes,
            ).lastActivityAt shouldBe now - 20.minutes
        }

        @Test
        fun `no metadata at all leaves the item without a sighting`() {
            val item = deviceItem(lastSeen = null, metaModifiedAt = null)

            item.lastSeen.shouldBeNull()
            item.lastActivityAt shouldBe Instant.DISTANT_PAST
        }
    }
}
