package eu.darken.octi.sync.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
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
        val contributions: List<ConnectorUiContribution> = emptyList(),
    )

    val state = flow {
        emit(State(contributions = contributions.values.sortedBy { it.displayOrder }))
    }.asStateFlow()

    fun onContributionClicked(contribution: ConnectorUiContribution, mode: Nav.Sync.AddPicker.Mode) {
        log(TAG) { "onContributionClicked(type=${contribution.type}, mode=$mode)" }
        when (mode) {
            Nav.Sync.AddPicker.Mode.CREATE -> navTo(contribution.addAccountDestination())
            Nav.Sync.AddPicker.Mode.LINK -> contribution.joinDeviceDestination()?.let { navTo(it) }
        }
    }

    companion object {
        /** LINK mode only lists backends that can join an existing account — no dead taps. */
        fun List<ConnectorUiContribution>.forMode(
            mode: Nav.Sync.AddPicker.Mode,
        ): List<ConnectorUiContribution> = when (mode) {
            Nav.Sync.AddPicker.Mode.CREATE -> this
            Nav.Sync.AddPicker.Mode.LINK -> filter { it.joinDeviceDestination() != null }
        }

        private val TAG = logTag("Sync", "Add", "VM")
    }
}
