package eu.darken.octi.module.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class ModuleSettingsVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel3(dispatcherProvider) {

    companion object {
        private val TAG = logTag("Settings", "Module", "VM")
    }
}