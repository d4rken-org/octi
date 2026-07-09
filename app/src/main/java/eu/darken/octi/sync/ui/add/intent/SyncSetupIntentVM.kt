package eu.darken.octi.sync.ui.add.intent

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import javax.inject.Inject

@HiltViewModel
class SyncSetupIntentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    fun goNew() {
        log(TAG) { "goNew()" }
        navTo(Nav.Sync.AddPicker(Nav.Sync.AddPicker.Mode.CREATE))
    }

    fun goLink() {
        log(TAG) { "goLink()" }
        navTo(Nav.Sync.AddPicker(Nav.Sync.AddPicker.Mode.LINK))
    }

    companion object {
        private val TAG = logTag("Sync", "Setup", "Intent", "VM")
    }
}
