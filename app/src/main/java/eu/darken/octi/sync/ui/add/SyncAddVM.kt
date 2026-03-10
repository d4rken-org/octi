package eu.darken.octi.sync.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SyncAddVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    data class State(
        val items: List<SyncAddItem> = emptyList(),
    )

    data class SyncAddItem(
        val type: SyncType,
        val onClick: () -> Unit,
    )

    enum class SyncType { GDRIVE, KSERVER }

    val state = flow {
        val items = listOf(
            SyncAddItem(SyncType.GDRIVE) { navTo(Nav.Sync.AddGDrive) },
            SyncAddItem(SyncType.KSERVER) { navTo(Nav.Sync.AddKServer) },
        )
        emit(State(items = items))
    }.asStateFlow()

    companion object {
        private val TAG = logTag("Sync", "Add", "VM")
    }
}