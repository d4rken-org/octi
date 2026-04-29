package eu.darken.octi.sync.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.withPrevious
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.StorageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SyncListVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @AppScope private val appScope: CoroutineScope,
    private val syncManager: SyncManager,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
    private val connectorOverviewProvider: ConnectorOverviewProvider,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    data class State(
        val connectors: List<ConnectorItem> = emptyList(),
        val highlightedConnectorIds: Set<ConnectorId> = emptySet(),
        val isPro: Boolean = false,
        val deviceId: String = "",
    )

    data class ConnectorItem(
        val connectorId: ConnectorId,
        val connector: SyncConnector,
        val ourState: SyncConnectorState,
        val storageStatus: StorageStatus,
        val otherStates: Collection<SyncConnectorState>,
        val pauseReason: ConnectorPauseReason?,
        val isPaused: Boolean,
        val isBusy: Boolean,
        val issues: List<ConnectorIssue> = emptyList(),
    )

    private val highlightedIds = MutableStateFlow<Set<ConnectorId>>(emptySet())
    private val highlightJobs = mutableMapOf<ConnectorId, Job>()

    init {
        launch {
            val isEmpty = syncManager.allConnectors.first().isEmpty()
            if (isEmpty) addConnector()
        }

        syncManager.allConnectors
            .map { connectors -> connectors.map { it.identifier }.toSet() }
            .withPrevious()
            .onEach { (previous, current) ->
                if (previous == null) return@onEach
                val newIds = current - previous
                if (newIds.isNotEmpty()) {
                    log(TAG, INFO) { "New connectors detected: $newIds" }
                    highlightedIds.value = highlightedIds.value + newIds
                    newIds.forEach { id ->
                        highlightJobs[id]?.cancel()
                        highlightJobs[id] = vmScope.launch {
                            delay(2.seconds)
                            highlightedIds.value = highlightedIds.value - id
                            highlightJobs.remove(id)
                        }
                    }
                }
            }.launchInViewModel()
    }

    val state = connectorOverviewProvider.cards
        .map { cards ->
            // Cross-cutting `otherStates` (used by DevicesField for distinct device counting)
            // is derived from sibling cards rather than re-fetched per connector.
            cards.map { card ->
                ConnectorItem(
                    connectorId = card.connector.identifier,
                    connector = card.connector,
                    ourState = card.syncState,
                    storageStatus = card.storageStatus,
                    otherStates = cards.filter { it.connector.identifier != card.connector.identifier }
                        .map { it.syncState },
                    pauseReason = card.pauseReason,
                    isPaused = card.isPaused,
                    isBusy = card.isBusy,
                    issues = card.issues,
                )
            }
        }
        .combine(highlightedIds) { connectors, highlighted -> connectors to highlighted }
        .combine(upgradeRepo.upgradeInfo) { (connectors, highlighted), upgradeInfo ->
            State(
                connectors = connectors,
                highlightedConnectorIds = highlighted,
                isPro = upgradeInfo.isPro,
                deviceId = syncSettings.deviceId.id,
            )
        }
        .asStateFlow()

    fun addConnector() {
        log(TAG) { "addConnector()" }
        navTo(Nav.Sync.Add)
    }

    fun togglePause(connectorId: ConnectorId) = launch {
        log(TAG) { "togglePause($connectorId)" }
        val pauseReason = syncSettings.pauseReason(connectorId)
        val isRepairResume = pauseReason == ConnectorPauseReason.AuthIssue
        if (!upgradeRepo.isPro() && !isRepairResume) {
            navTo(Nav.Main.Upgrade())
            return@launch
        }
        syncManager.togglePause(connectorId)
    }

    fun forceSync(connectorId: ConnectorId) = launch(appScope) {
        log(TAG) { "forceSync($connectorId)" }
        syncManager.sync(connectorId)
    }

    fun disconnect(connectorId: ConnectorId) = launch(appScope) {
        log(TAG) { "disconnect($connectorId)" }
        syncManager.disconnect(connectorId)
    }

    fun resetData(connectorId: ConnectorId) = launch(appScope) {
        log(TAG) { "resetData($connectorId)" }
        syncManager.resetData(connectorId)
    }

    fun viewDevices(connectorId: ConnectorId) {
        log(TAG) { "viewDevices($connectorId)" }
        navTo(Nav.Sync.Devices(connectorId.idString))
    }

    fun linkNewDevice(destination: NavigationDestination) {
        log(TAG) { "linkNewDevice($destination)" }
        navTo(destination)
    }

    companion object {
        private val TAG = logTag("Sync", "List", "VM")
    }
}
