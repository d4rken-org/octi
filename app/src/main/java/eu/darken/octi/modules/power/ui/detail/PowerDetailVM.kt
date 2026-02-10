package eu.darken.octi.modules.power.ui.detail

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PowerDetailVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    powerRepo: PowerRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: PowerDetailFragmentArgs by handle.navArgs()

    data class State(
        val powerData: ModuleData<PowerInfo>? = null,
    )

    val state = powerRepo.state
        .map { repoState ->
            val data = repoState.all.firstOrNull { it.deviceId == navArgs.deviceId }
            State(powerData = data)
        }
        .asLiveData2()

    companion object {
        private val TAG = logTag("Module", "Power", "Detail", "VM")
    }
}
