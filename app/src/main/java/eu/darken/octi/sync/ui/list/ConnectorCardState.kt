package eu.darken.octi.sync.ui.list

import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.blob.StorageStatus

/**
 * UI-shaped join of one connector's sync state, paused/busy flags, issues, and storage status.
 * Produced by [ConnectorOverviewProvider]. Consumed by `SyncListVM` and (potentially) other
 * connector-card surfaces.
 */
data class ConnectorCardState(
    val connector: SyncConnector,
    val syncState: SyncConnectorState,
    val storageStatus: StorageStatus,
    val isPaused: Boolean,
    val isBusy: Boolean,
    val issues: List<ConnectorIssue>,
)
