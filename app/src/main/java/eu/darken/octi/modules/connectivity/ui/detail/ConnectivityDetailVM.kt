package eu.darken.octi.modules.connectivity.ui.detail

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.core.ConnectivityRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class ConnectivityDetailVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    connectivityRepo: ConnectivityRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: ConnectivityDetailFragmentArgs by handle.navArgs()

    data class State(
        val connectivityInfo: ConnectivityInfo?,
    )

    val state = connectivityRepo.state.map { repoState ->
        val moduleData = repoState.all.firstOrNull { it.deviceId == navArgs.deviceId }
        State(connectivityInfo = moduleData?.data)
    }.asLiveData2()

    companion object {
        private val TAG = logTag("Module", "Connectivity", "Detail", "VM")
    }
}
