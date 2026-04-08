package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.replayingShare
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
) {

    val issues: Flow<List<ConnectorIssue>> = combine(
        syncManager.allStates,
        syncManager.allConnectors,
        clockAnalyzer.analysis,
    ) { states, connectors, clockAnalysis ->
        val connectorIssues = states.flatMap { it.issues }
        val metadataIssues = buildMetadataIssues(connectors, states)
        val clockIssues = buildClockIssues(connectors, states, clockAnalysis)
        val all = connectorIssues + metadataIssues + clockIssues
        log(TAG, VERBOSE) { "issues: ${all.size} total (${connectorIssues.size} connector, ${metadataIssues.size} metadata, ${clockIssues.size} clock)" }
        all
    }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "issues" }
        .replayingShare(scope + dispatcherProvider.Default)

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
