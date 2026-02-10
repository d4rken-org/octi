package eu.darken.octi.modules.wifi.ui.detail

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.core.WifiRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class WifiDetailVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    wifiRepo: WifiRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: WifiDetailFragmentArgs by handle.navArgs()

    data class State(
        val wifiData: ModuleData<WifiInfo>? = null,
    )

    val state = wifiRepo.state
        .map { repoState ->
            val data = repoState.all.firstOrNull { it.deviceId == navArgs.deviceId }
            State(wifiData = data)
        }
        .asLiveData2()

    companion object {
        private val TAG = logTag("Module", "Wifi", "Detail", "VM")
    }
}
