package eu.darken.octi.sync.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector
import eu.darken.octi.syncs.gdrive.ui.GDriveStateVH
import eu.darken.octi.syncs.jserver.core.JServerConnector
import eu.darken.octi.syncs.jserver.ui.JServerStateVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SyncListVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    syncManager: eu.darken.octi.sync.core.SyncManager,
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

    val state = syncManager.connectors
        .flatMapLatest { connectors ->
            if (connectors.isEmpty()) return@flatMapLatest flowOf(emptyList())

            val withStates = connectors.map { connector ->
                connector.state.mapNotNull { state ->
                    when (connector) {
                        is GDriveAppDataConnector -> GDriveStateVH.Item(
                            account = connector.account,
                            ourState = state,
                            otherStates = (connectors - connector).map { it.state.first() },
                            onManage = {
                                SyncListFragmentDirections.actionSyncListFragmentToGDriveActionsFragment(
                                    connector.identifier
                                ).navigate()
                            }
                        )
                        is JServerConnector -> JServerStateVH.Item(
                            credentials = connector.credentials,
                            ourState = state,
                            otherStates = (connectors - connector).map { it.state.first() },
                            onManage = {
                                SyncListFragmentDirections.actionSyncListFragmentToSyrvJServerActionsFragment(
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