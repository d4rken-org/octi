package eu.darken.octi.sync.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.withPrevious
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.StalenessUtil.countStaleDevices
import eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector
import eu.darken.octi.syncs.gdrive.core.GoogleAccount
import eu.darken.octi.syncs.kserver.core.KServer
import eu.darken.octi.syncs.kserver.core.KServerConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class SyncListVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    @AppScope private val appScope: CoroutineScope,
    private val syncManager: SyncManager,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    data class State(
        val connectors: List<ConnectorItem> = emptyList(),
        val highlightedConnectorIds: Set<ConnectorId> = emptySet(),
    )

    sealed interface ConnectorItem {
        val connectorId: ConnectorId

        data class GDrive(
            override val connectorId: ConnectorId,
            val account: GoogleAccount,
            val ourState: SyncConnectorState,
            val otherStates: Collection<SyncConnectorState>,
            val isPaused: Boolean,
            val staleDevicesCount: Int,
        ) : ConnectorItem

        data class KServer(
            override val connectorId: ConnectorId,
            val credentials: KServer.Credentials,
            val ourState: SyncConnectorState,
            val otherStates: Collection<SyncConnectorState>,
            val isPaused: Boolean,
            val staleDevicesCount: Int,
        ) : ConnectorItem
    }

    private val highlightedIds = MutableStateFlow<Set<ConnectorId>>(emptySet())
    private val highlightJobs = mutableMapOf<ConnectorId, Job>()

    init {
        launch {
            val isEmpty = syncManager.connectors.first().isEmpty()
            if (isEmpty) addConnector()
        }

        syncManager.connectors
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
                            delay(2_000)
                            highlightedIds.value = highlightedIds.value - id
                            highlightJobs.remove(id)
                        }
                    }
                }
            }.launchInViewModel()
    }

    val state = syncSettings.pausedConnectors.flow
        .combine(syncManager.connectors) { paused, connectorList -> paused to connectorList }
        .flatMapLatest { (paused, connectors) ->
            if (connectors.isEmpty()) return@flatMapLatest flowOf(emptyList())

            val withStates = connectors.map { connector ->
                combineTransform(connector.state, connector.data) { state, data ->
                    val item = when (connector) {
                        is GDriveAppDataConnector -> ConnectorItem.GDrive(
                            connectorId = connector.identifier,
                            account = connector.account,
                            ourState = state,
                            otherStates = (connectors - connector).map { it.state.first() },
                            isPaused = paused.contains(connector.identifier),
                            staleDevicesCount = data.countStaleDevices(),
                        )

                        is KServerConnector -> ConnectorItem.KServer(
                            connectorId = connector.identifier,
                            credentials = connector.credentials,
                            ourState = state,
                            otherStates = (connectors - connector).map { it.state.first() },
                            isPaused = paused.contains(connector.identifier),
                            staleDevicesCount = data.countStaleDevices(),
                        )

                        else -> {
                            log(TAG, WARN) { "Unknown connector type: $connector" }
                            null
                        }
                    }
                    if (item != null) emit(item)
                }
            }

            combine(withStates) { it.toList() }
        }
        .combine(highlightedIds) { connectors, highlighted ->
            State(connectors = connectors, highlightedConnectorIds = highlighted)
        }
        .asStateFlow()

    fun addConnector() {
        log(TAG) { "addConnector()" }
        navTo(Nav.Sync.Add)
    }

    fun togglePause(connectorId: ConnectorId) = launch {
        log(TAG) { "togglePause($connectorId)" }
        if (!upgradeRepo.isPro()) {
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

    fun linkNewDevice(connectorId: ConnectorId) {
        log(TAG) { "linkNewDevice($connectorId)" }
        navTo(Nav.Sync.KServerLinkHost(connectorId.idString))
    }

    companion object {
        private val TAG = logTag("Sync", "List", "VM")
    }
}
