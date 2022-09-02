package eu.darken.octi.sync.ui.list

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncRepo
import eu.darken.octi.sync.core.provider.gdrive.GDriveAppDataConnector
import eu.darken.octi.sync.ui.list.items.GDriveAppDataVH
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SyncListVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val syncRepo: SyncRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val connectors: List<SyncListAdapter.Item> = emptyList()
    )

    val state = syncRepo.connectors.map { connectors ->
        val infos = connectors.mapNotNull {
            when (it) {
                is GDriveAppDataConnector -> GDriveAppDataVH.Item(
                    account = it.account,
                    state = it.state.first()
                )
                else -> {
                    log(TAG, WARN) { "Unknown cnnector type: $it" }
                    null
                }
            }
        }
        State(
            connectors = infos
        )
    }.asLiveData2()

    fun addConnector() {
        log(TAG) { "addConnector()" }
        SyncListFragmentDirections.actionSyncListFragmentToSyncAddFragment().navigate()
    }

    companion object {
        private val TAG = logTag("Sync", "List", "Fragment", "VM")
    }
}