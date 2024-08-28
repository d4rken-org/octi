package eu.darken.octi.syncs.gdrive.ui.actions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.getConnectorById
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GDriveActionsVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: eu.darken.octi.sync.core.SyncManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: GDriveActionsFragmentArgs by handle.navArgs()

    data class State(
        val account: eu.darken.octi.syncs.gdrive.core.GoogleAccount
    )

    val state =
        syncManager.getConnectorById<eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector>(navArgs.identifier)
            .map {
                State(it.account)
            }
            .catch {
                if (it is NoSuchElementException) popNavStack()
                else throw it
            }
            .asLiveData2()

    fun disconnct() = launch {
        log(TAG) { "disconnct()" }
        syncManager.disconnect(navArgs.identifier)
        popNavStack()
    }

    fun reset() = launch {
        log(TAG) { "reset()" }
        syncManager.resetData(navArgs.identifier)
        popNavStack()
    }

    fun forceSync() = launch {
        log(TAG) { "forceSync()" }
        syncManager.sync(navArgs.identifier)
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "Actions", "Fragment", "VM")
    }
}