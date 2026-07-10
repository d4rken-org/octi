package eu.darken.octi.main.ui.dashboard

import eu.darken.octi.main.ui.dashboard.DashboardVM.Companion.buildSyncHintCtx
import eu.darken.octi.main.ui.dashboard.DashboardVM.Companion.isSyncedButAlone
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.SyncConnectorState
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import kotlin.time.Instant

class SyncHintsTest : BaseTest() {

    private val selfId = DeviceId("self")
    private val peerId = DeviceId("peer")

    private fun state(
        lastSyncAt: Instant? = Instant.parse("2026-07-01T12:00:00Z"),
        lastError: Exception? = null,
        deviceIds: List<DeviceId> = emptyList(),
    ): SyncConnectorState = mockk<SyncConnectorState>().apply {
        every { this@apply.lastSyncAt } returns lastSyncAt
        every { this@apply.lastError } returns lastError
        every { this@apply.deviceMetadata } returns deviceIds.map { id ->
            mockk<DeviceMetadata>().apply { every { deviceId } returns id }
        }
    }

    @Nested
    inner class `isSyncedButAlone predicate` {

        private fun alone(
            connectorCount: Int = 1,
            activeConnectorCount: Int = 1,
            activeSyncedCount: Int = 1,
            activeErrorCount: Int = 0,
            knownPeerCount: Int = 0,
            visiblePeerCount: Int = 0,
        ) = isSyncedButAlone(
            connectorCount = connectorCount,
            activeConnectorCount = activeConnectorCount,
            activeSyncedCount = activeSyncedCount,
            activeErrorCount = activeErrorCount,
            knownPeerCount = knownPeerCount,
            visiblePeerCount = visiblePeerCount,
        )

        @Test
        fun `synced with no peers is alone`() {
            alone() shouldBe true
        }

        @Test
        fun `no connectors is not alone`() {
            alone(connectorCount = 0, activeConnectorCount = 0, activeSyncedCount = 0) shouldBe false
        }

        @Test
        fun `fresh connector that has not synced yet is not alone`() {
            alone(activeSyncedCount = 0) shouldBe false
        }

        @Test
        fun `one synced one not is not alone`() {
            alone(connectorCount = 2, activeConnectorCount = 2, activeSyncedCount = 1) shouldBe false
        }

        @Test
        fun `erroring connector is not alone`() {
            alone(activeErrorCount = 1) shouldBe false
        }

        @Test
        fun `peer known in metadata is not alone`() {
            alone(knownPeerCount = 1) shouldBe false
        }

        @Test
        fun `visible cached peer is not alone`() {
            alone(visiblePeerCount = 1) shouldBe false
        }

        @Test
        fun `paused-only connectors are not alone`() {
            alone(activeConnectorCount = 0, activeSyncedCount = 0) shouldBe false
        }
    }

    @Nested
    inner class `buildSyncHintCtx projection` {

        @Test
        fun `counts synced and erroring active states`() {
            val ctx = buildSyncHintCtx(
                connectorCount = 3,
                activeStates = listOf(
                    state(),
                    state(lastSyncAt = null),
                    state(lastError = Exception("boom")),
                ),
                allStates = emptyList(),
                snoozedCards = emptySet(),
                selfDeviceId = selfId,
            )
            ctx.connectorCount shouldBe 3
            ctx.activeConnectorCount shouldBe 3
            ctx.activeSyncedCount shouldBe 2
            ctx.activeErrorCount shouldBe 1
        }

        @Test
        fun `known peers exclude the local device`() {
            val ctx = buildSyncHintCtx(
                connectorCount = 1,
                activeStates = listOf(state()),
                allStates = listOf(state(deviceIds = listOf(selfId, peerId))),
                snoozedCards = emptySet(),
                selfDeviceId = selfId,
            )
            ctx.knownPeerIds shouldContainExactly setOf(peerId)
        }

        @Test
        fun `peers from paused connectors still count`() {
            val ctx = buildSyncHintCtx(
                connectorCount = 1,
                activeStates = emptyList(),
                allStates = listOf(state(deviceIds = listOf(peerId))),
                snoozedCards = emptySet(),
                selfDeviceId = selfId,
            )
            ctx.knownPeerIds shouldContainExactly setOf(peerId)
        }

        @Test
        fun `snoozed cards flow through`() {
            val ctx = buildSyncHintCtx(
                connectorCount = 0,
                activeStates = emptyList(),
                allStates = emptyList(),
                snoozedCards = setOf(DashboardCardSnoozer.Card.SYNC_SETUP),
                selfDeviceId = selfId,
            )
            ctx.snoozedCards shouldContainExactly setOf(DashboardCardSnoozer.Card.SYNC_SETUP)
        }
    }

    @Nested
    inner class `card snoozer` {

        @Test
        fun `snoozes accumulate and cards are independent`() = runTest2 {
            val snoozer = DashboardCardSnoozer()
            snoozer.snoozedCards.first() shouldBe emptySet()

            snoozer.snooze(DashboardCardSnoozer.Card.SYNC_SETUP)
            snoozer.snoozedCards.first() shouldBe setOf(DashboardCardSnoozer.Card.SYNC_SETUP)

            snoozer.snooze(DashboardCardSnoozer.Card.SYNCED_ALONE)
            snoozer.snoozedCards.first() shouldBe setOf(
                DashboardCardSnoozer.Card.SYNC_SETUP,
                DashboardCardSnoozer.Card.SYNCED_ALONE,
            )
        }
    }
}
