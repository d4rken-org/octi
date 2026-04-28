package eu.darken.octi.sync.core

import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.StorageStatusManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import kotlin.time.Clock

class ConnectorIssueAggregatorTest : BaseTest() {

    private val deviceId = DeviceId("device-self")
    private val connectorA = ConnectorId(ConnectorType.OCTISERVER, "a.example.com", "acc-a")
    private val connectorB = ConnectorId(ConnectorType.OCTISERVER, "b.example.com", "acc-b")

    private fun snap(
        connectorId: ConnectorId,
        used: Long,
        total: Long,
        available: Long = total - used,
    ) = StorageSnapshot(
        connectorId = connectorId,
        accountLabel = null,
        usedBytes = used,
        totalBytes = total,
        availableBytes = available,
        maxFileBytes = null,
        perFileOverheadBytes = 0L,
        updatedAt = Clock.System.now(),
    )

    private fun connector(id: ConnectorId): SyncConnector = mockk(relaxed = true) {
        every { identifier } returns id
    }

    private fun connectorState(): SyncConnectorState = mockk(relaxed = true) {
        every { issues } returns emptyList()
        every { deviceMetadata } returns emptyList()
        every { isAvailable } returns true
    }

    private fun createAggregator(
        scope: kotlinx.coroutines.CoroutineScope,
        connectors: List<SyncConnector>,
        states: Collection<SyncConnectorState> = connectors.map { connectorState() },
        rejections: Map<ConnectorId, BlobManager.RejectionReason> = emptyMap(),
        storage: Map<ConnectorId, StorageStatus> = emptyMap(),
    ): ConnectorIssueAggregator {
        val ownDeviceId = deviceId
        val syncManager = mockk<SyncManager>(relaxed = true).also {
            every { it.allConnectors } returns MutableStateFlow(connectors)
            every { it.allStates } returns MutableStateFlow(states)
        }
        val clockAnalyzer = mockk<ClockAnalyzer>(relaxed = true).also {
            every { it.analysis } returns MutableStateFlow(null)
        }
        val syncSettings = mockk<SyncSettings>(relaxed = true).also {
            every { it.deviceId } returns ownDeviceId
        }
        val blobManager = mockk<BlobManager>(relaxed = true).also {
            every { it.connectorRejections } returns MutableStateFlow(rejections)
        }
        val storageStatusManager = mockk<StorageStatusManager>(relaxed = true).also {
            every { it.statuses } returns MutableStateFlow(storage)
        }
        return ConnectorIssueAggregator(
            scope = scope,
            dispatcherProvider = TestDispatcherProvider(),
            syncManager = syncManager,
            clockAnalyzer = clockAnalyzer,
            syncSettings = syncSettings,
            blobManager = blobManager,
            storageStatusManager = storageStatusManager,
        )
    }

    @Test
    fun `LowStorage emitted when Ready snapshot is below the threshold`() = runTest2 {
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA)),
            storage = mapOf(connectorA to StorageStatus.Ready(connectorA, snap(connectorA, used = 95, total = 100))),
        )

        val issues = aggregator.issues.first()

        issues shouldContain CommonIssue.LowStorage(connectorId = connectorA, deviceId = deviceId)
    }

    @Test
    fun `LowStorage not emitted for healthy snapshot`() = runTest2 {
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA)),
            storage = mapOf(connectorA to StorageStatus.Ready(connectorA, snap(connectorA, used = 50, total = 100))),
        )

        aggregator.issues.first().filterIsInstance<CommonIssue.LowStorage>().shouldBeEmpty()
    }

    @Test
    fun `LowStorage not emitted for Loading state even if lastKnown is below threshold`() = runTest2 {
        // Stale lastKnown shouldn't drive the dashboard chip — Ready-only policy.
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA)),
            storage = mapOf(
                connectorA to StorageStatus.Loading(
                    connectorId = connectorA,
                    lastKnown = snap(connectorA, used = 99, total = 100),
                ),
            ),
        )

        aggregator.issues.first().filterIsInstance<CommonIssue.LowStorage>().shouldBeEmpty()
    }

    @Test
    fun `LowStorage suppressed when AccountQuotaFull rejection is active for the same connector`() = runTest2 {
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA)),
            rejections = mapOf(connectorA to BlobManager.RejectionReason.AccountQuotaFull),
            storage = mapOf(connectorA to StorageStatus.Ready(connectorA, snap(connectorA, used = 99, total = 100))),
        )

        val issues = aggregator.issues.first()
        issues shouldNotContain CommonIssue.LowStorage(connectorId = connectorA, deviceId = deviceId)
        issues shouldContain CommonIssue.AccountQuotaFull(connectorId = connectorA, deviceId = deviceId)
    }

    @Test
    fun `LowStorage suppressed when ServerStorageLow rejection is active for the same connector`() = runTest2 {
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA)),
            rejections = mapOf(connectorA to BlobManager.RejectionReason.ServerStorageLow),
            storage = mapOf(connectorA to StorageStatus.Ready(connectorA, snap(connectorA, used = 99, total = 100))),
        )

        val issues = aggregator.issues.first()
        issues shouldNotContain CommonIssue.LowStorage(connectorId = connectorA, deviceId = deviceId)
        issues shouldContain CommonIssue.ServerStorageLow(connectorId = connectorA, deviceId = deviceId)
    }

    @Test
    fun `LowStorage filtered out for unconfigured connector`() = runTest2 {
        // connectorB has a low snapshot but isn't in the configured connectors list.
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA)),
            storage = mapOf(
                connectorA to StorageStatus.Ready(connectorA, snap(connectorA, used = 50, total = 100)),
                connectorB to StorageStatus.Ready(connectorB, snap(connectorB, used = 99, total = 100)),
            ),
        )

        val low = aggregator.issues.first().filterIsInstance<CommonIssue.LowStorage>()
        low.shouldBeEmpty()
    }

    @Test
    fun `LowStorage emits one entry per low connector`() = runTest2 {
        val aggregator = createAggregator(
            scope = backgroundScope,
            connectors = listOf(connector(connectorA), connector(connectorB)),
            storage = mapOf(
                connectorA to StorageStatus.Ready(connectorA, snap(connectorA, used = 95, total = 100)),
                connectorB to StorageStatus.Ready(connectorB, snap(connectorB, used = 99, total = 100)),
            ),
        )

        val low = aggregator.issues.first().filterIsInstance<CommonIssue.LowStorage>()
        low.map { it.connectorId } shouldContainExactlyInAnyOrder listOf(connectorA, connectorB)
    }

    @Test
    fun `LowStorage equality is identity-only — byte fluctuations don't churn`() {
        // Critical for distinctUntilChanged on the issues flow: a refresh that shifts byte counts
        // but keeps the connector still-low must not produce a new equal-distinct list entry.
        val a = CommonIssue.LowStorage(connectorId = connectorA, deviceId = deviceId)
        val b = CommonIssue.LowStorage(connectorId = connectorA, deviceId = deviceId)
        a shouldBe b
    }
}
