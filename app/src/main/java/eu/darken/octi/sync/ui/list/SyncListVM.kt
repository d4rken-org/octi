package eu.darken.octi.sync.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector
import eu.darken.octi.syncs.gdrive.ui.GDriveStateVH
import eu.darken.octi.syncs.kserver.core.KServerConnector
import eu.darken.octi.syncs.kserver.ui.KServerStateVH
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

@HiltViewModel
class SyncListVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    syncManager: SyncManager,
    private val syncSettings: SyncSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val connectors: List<SyncListAdapter.Item> = emptyList()
    )

    init {
        launch {
            val isEmpty = syncManager.connectors.first().isEmpty()
            if (isEmpty) addConnector()
        }
    }

    val state = syncSettings.pausedConnectors.flow
        .combine(syncManager.connectors) { paused, connectorList -> paused to connectorList }
        .flatMapLatest { (paused, connectors) ->
            if (connectors.isEmpty()) return@flatMapLatest flowOf(emptyList())

            val withStates = connectors.map { connector ->
                connector.state.mapNotNull { state ->
                    when (connector) {
                        is GDriveAppDataConnector -> GDriveStateVH.Item(
                            account = connector.account,
                            ourState = state,
                            otherStates = (connectors - connector).map { it.state.first() },
                            isPaused = paused.contains(connector.identifier),
                            onManage = {
                                SyncListFragmentDirections.actionSyncListFragmentToGDriveActionsFragment(
                                    connector.identifier
                                ).navigate()
                            }
                        )

                        is KServerConnector -> KServerStateVH.Item(
                            credentials = connector.credentials,
                            ourState = state,
                            otherStates = (connectors - connector).map { it.state.first() },
                            isPaused = paused.contains(connector.identifier),
                            onManage = {
                                SyncListFragmentDirections.actionSyncListFragmentToKServerActionsFragment(
                                    connector.identifier
                                ).navigate()
                            }
                        )

                        else -> {
                            log(TAG, WARN) { "Unknown connector type: $connector" }
                            null
                        }
                    }
                }
            }

            combine(withStates) { it.toList() }
        }
        .map {
            State(
                connectors = it
            )
        }
        .asLiveData2()

    fun addConnector() {
        log(TAG) { "addConnector()" }
        SyncListFragmentDirections.actionSyncListFragmentToSyncAddFragment().navigate()
    }

    companion object {
        private val TAG = logTag("Sync", "List", "Fragment", "VM")
    }
}