package eu.darken.octi.sync.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.syrvs.gdrive.core.GDriveAppDataConnector
import eu.darken.octi.syrvs.gdrive.core.GoogleAccountRepo
import eu.darken.octi.syrvs.gdrive.ui.GDriveStateVH
import eu.darken.octi.syrvs.jserver.core.JServerAccountRepo
import eu.darken.octi.syrvs.jserver.core.JServerConnector
import eu.darken.octi.syrvs.jserver.ui.JServerStateVH
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SyncListVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    syncManager: SyncManager,
    private val googleAccountRepo: GoogleAccountRepo,
    private val jServerAccountRepo: JServerAccountRepo,
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
                connector.state.mapNotNull<SyncConnector.State, SyncListAdapter.Item> { state ->
                    when (connector) {
                        is GDriveAppDataConnector -> GDriveStateVH.Item(
                            account = connector.account,
                            state = state,
                            onDisconnect = { launch { googleAccountRepo.remove(it.id) } },
                            onWipe = { launch { connector.wipe() } }
                        )
                        is JServerConnector -> JServerStateVH.Item(
                            credentials = connector.credentials,
                            state = state,
                            onDisconnect = { launch { jServerAccountRepo.remove(it.accountId) } },
                            onWipe = { launch { connector.wipe() } }
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