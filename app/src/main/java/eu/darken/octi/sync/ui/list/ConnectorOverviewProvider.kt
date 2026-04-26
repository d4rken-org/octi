package eu.darken.octi.sync.ui.list

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorIssueAggregator
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.StorageStatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Joins per-connector sync state, paused/busy flags, issues, and storage status into a single
 * [ConnectorCardState] list shaped for connector-card UIs. Sync-list and any future connector
 * dashboard consume this — the join logic lives here, not in each VM.
 *
 * Storage join uses `Map<ConnectorId, StorageStatus>` lookup (not list-position pairing) so
 * connector add/remove mid-flow doesn't desync the cards.
 */
@Singleton
class ConnectorOverviewProvider @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val storageStatusManager: StorageStatusManager,
    private val syncSettings: SyncSettings,
    private val issueAggregator: ConnectorIssueAggregator,
) {

    val cards: Flow<List<ConnectorCardState>> = combine(
        syncManager.allConnectors,
        syncSettings.pausedConnectors.flow,
        storageStatusManager.statuses,
        issueAggregator.issues,
    ) { connectors, paused, statuses, allIssues ->
        Quad(connectors, paused, statuses, allIssues)
    }
        .flatMapLatest { (connectors, paused, statuses, allIssues) ->
            if (connectors.isEmpty()) flowOf(emptyList())
            else combine(connectors.map { c ->
                combine(c.state, c.operations) { state, ops ->
                    val issues = allIssues.filter { it.connectorId == c.identifier }
                    val isBusy = ops.any { it is ConnectorOperation.Queued || it is ConnectorOperation.Processing }
                    ConnectorCardState(
                        connector = c,
                        syncState = state,
                        storageStatus = statuses[c.identifier] ?: StorageStatus.Unsupported(c.identifier),
                        isPaused = c.identifier in paused,
                        isBusy = isBusy,
                        issues = issues,
                    )
                }
            }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG) { "cards" }
        .shareLatest(scope + dispatcherProvider.Default)

    private data class Quad<A, B, C, D>(
        val a: A, val b: B, val c: C, val d: D,
    )

    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <A, B, C, D> Quad<A, B, C, D>.component1(): A = a
    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <A, B, C, D> Quad<A, B, C, D>.component2(): B = b
    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <A, B, C, D> Quad<A, B, C, D>.component3(): C = c
    @Suppress("NOTHING_TO_INLINE")
    private inline operator fun <A, B, C, D> Quad<A, B, C, D>.component4(): D = d

    companion object {
        private val TAG = logTag("Sync", "ConnectorOverview")
    }
}
