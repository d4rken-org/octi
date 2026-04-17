package eu.darken.octi.sync.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.common.uix.ViewModel4
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class SyncAddVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val contributions: Map<ConnectorType, @JvmSuppressWildcards ConnectorUiContribution>,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    data class State(
        val items: List<SyncAddItem> = emptyList(),
    )

    data class SyncAddItem(
        val contribution: ConnectorUiContribution,
        val onClick: () -> Unit,
    )

    val state = flow {
        val items = contributions.values
            .sortedBy { it.displayOrder }
            .map { contribution ->
                SyncAddItem(contribution) { navTo(contribution.addAccountDestination()) }
            }
        emit(State(items = items))
    }.asStateFlow()

    companion object {
        private val TAG = logTag("Sync", "Add", "VM")
    }
}
