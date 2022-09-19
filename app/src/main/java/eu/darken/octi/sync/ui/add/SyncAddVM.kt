package eu.darken.octi.sync.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.syncs.gdrive.ui.add.AddGDriveVH
import eu.darken.octi.syncs.jserver.ui.add.AddJServerDataVH
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SyncAddVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {


    val addItems = flow {
        val items = mutableListOf<SyncAddAdapter.Item>()

        AddGDriveVH.Item {
            SyncAddFragmentDirections.actionSyncAddFragmentToGDriveAddFragment().navigate()
        }.run { items.add(this) }
        AddJServerDataVH.Item {
            SyncAddFragmentDirections.actionSyncAddFragmentToAddJServerFragment().navigate()
        }.run { items.add(this) }

        emit(items)
    }.asLiveData2()


    companion object {
        private val TAG = logTag("Sync", "Add", "Fragment", "VM")
    }
}