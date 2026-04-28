package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.StorageStatusManager
import eu.darken.octi.sync.core.blob.isLowStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectorIssueAggregator @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val clockAnalyzer: ClockAnalyzer,
    private val syncSettings: SyncSettings,
    private val blobManager: BlobManager,
    private val storageStatusManager: StorageStatusManager,
) {

    val issues: Flow<List<ConnectorIssue>> = combine(
        syncManager.allStates,
        syncManager.allConnectors,
        clockAnalyzer.analysis,
        blobManager.connectorRejections,
        storageStatusManager.statuses,
    ) { states, connectors, clockAnalysis, rejections, storageStatuses ->
        val configuredIds = connectors.map { it.identifier }.toSet()
        val connectorIssues = states.flatMap { it.issues }
        val metadataIssues = buildMetadataIssues(connectors, states)
        val clockIssues = buildClockIssues(connectors, states, clockAnalysis)
        val blobIssues = buildBlobIssues(rejections)
        val lowStorageIssues = buildLowStorageIssues(storageStatuses, configuredIds, blobIssues)
        val all = (connectorIssues + metadataIssues + clockIssues + blobIssues + lowStorageIssues).distinct()
        log(TAG, VERBOSE) {
            "issues: ${all.size} total (${connectorIssues.size} connector, " +
                "${metadataIssues.size} metadata, ${clockIssues.size} clock, " +
                "${blobIssues.size} blob, ${lowStorageIssues.size} low-storage)"
        }
        all
    }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "issues" }
        .replayingShare(scope + dispatcherProvider.Default)

    private fun buildBlobIssues(
        rejections: Map<ConnectorId, BlobManager.RejectionReason>,
    ): List<ConnectorIssue> = rejections.map { (connectorId, reason) ->
        when (reason) {
            BlobManager.RejectionReason.ServerStorageLow ->
                CommonIssue.ServerStorageLow(connectorId = connectorId, deviceId = syncSettings.deviceId)
            BlobManager.RejectionReason.AccountQuotaFull ->
                CommonIssue.AccountQuotaFull(connectorId = connectorId, deviceId = syncSettings.deviceId)
        }
    }

    /**
     * Proactive "running low on space" warnings derived from [StorageStatusManager.statuses].
     *
     * - `Ready` only: never derive a warning from a stale `lastKnown` (a `Loading` row that
     *   inherits a long-cached value can mislead the chip).
     * - Filtered by [configuredIds]: defends against statuses left behind by a connector that was
     *   removed mid-stream.
     * - Suppressed when a quota-related blob issue is already active for the same connector — the
     *   reactive [CommonIssue.AccountQuotaFull] / [CommonIssue.ServerStorageLow] outranks the
     *   proactive variant. Suppression keys off the *issue type* (not raw `RejectionReason`) so it
     *   stays correct if more rejection reasons are added that aren't quota-shaped.
     */
    private fun buildLowStorageIssues(
        storageStatuses: Map<ConnectorId, StorageStatus>,
        configuredIds: Set<ConnectorId>,
        blobIssues: List<ConnectorIssue>,
    ): List<ConnectorIssue> {
        val suppressed: Set<ConnectorId> = blobIssues
            .filter { it is CommonIssue.AccountQuotaFull || it is CommonIssue.ServerStorageLow }
            .map { it.connectorId }
            .toSet()
        return storageStatuses.values.asSequence()
            .filterIsInstance<StorageStatus.Ready>()
            .filter { it.connectorId in configuredIds }
            .filter { it.connectorId !in suppressed }
            .filter { it.snapshot.isLowStorage() }
            .map { CommonIssue.LowStorage(connectorId = it.connectorId, deviceId = syncSettings.deviceId) }
            .toList()
    }

    private fun buildMetadataIssues(
        connectors: List<SyncConnector>,
        states: Collection<SyncConnectorState>,
    ): List<ConnectorIssue> = buildList {
        val statesList = states.toList()
        connectors.zip(statesList).forEach { (connector, state) ->
            for (device in state.deviceMetadata) {
                if (device.deviceId == syncSettings.deviceId) continue

                val lastSeen = device.lastSeen
                if (lastSeen != null && StalenessUtil.isStale(lastSeen)) {
                    add(
                        CommonIssue.StaleDevice(
                            connectorId = connector.identifier,
                            deviceId = device.deviceId,
                            lastSeen = lastSeen,
                        )
                    )
                }

                val version = device.version
                if (version != null && VersionCompat.isOutdated(version)) {
                    add(
                        CommonIssue.OutdatedVersion(
                            connectorId = connector.identifier,
                            deviceId = device.deviceId,
                            version = version,
                        )
                    )
                }
            }
        }
    }

    private fun buildClockIssues(
        connectors: List<SyncConnector>,
        states: Collection<SyncConnectorState>,
        clockAnalysis: ClockAnalyzer.ClockAnalysis?,
    ): List<CommonIssue.ClockSkew> {
        if (clockAnalysis == null) return emptyList()

        val suspectIds = clockAnalysis.suspectDeviceIds +
                if (clockAnalysis.isCurrentDeviceSuspect) setOf(syncSettings.deviceId) else emptySet()

        if (suspectIds.isEmpty()) return emptyList()

        val statesList = states.toList()
        val connectorForDevice = mutableMapOf<DeviceId, ConnectorId>()
        connectors.zip(statesList).forEach { (connector, state) ->
            state.deviceMetadata.forEach { meta ->
                connectorForDevice.putIfAbsent(meta.deviceId, connector.identifier)
            }
        }

        return suspectIds.mapNotNull { deviceId ->
            val connectorId = connectorForDevice[deviceId] ?: return@mapNotNull null
            CommonIssue.ClockSkew(
                connectorId = connectorId,
                deviceId = deviceId,
            )
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Issues", "Aggregator")
    }
}
