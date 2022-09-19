package eu.darken.octi.syncs.gdrive.ui.actions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncManager
import javax.inject.Inject

@HiltViewModel
class GDriveActionsVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: GDriveActionsFragmentArgs by handle.navArgs()

    fun linkNewDevice() {
        log(TAG) { "linkNewDevice()" }
    }

    fun disconnct() = launch {
        log(TAG) { "disconnct()" }
        syncManager.disconnect(navArgs.identifier)
        navEvents.postValue(null)
    }

    fun wipe() = launch {
        log(TAG) { "wipe()" }
        syncManager.wipe(navArgs.identifier)
        navEvents.postValue(null)
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "Actions", "Fragment", "VM")
    }
}