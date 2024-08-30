package eu.darken.octi.syncs.gdrive.ui.actions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.getConnectorById
import eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector
import eu.darken.octi.syncs.gdrive.core.GoogleAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GDriveActionsVM @Inject constructor(
    handle: SavedStateHandle,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: GDriveActionsFragmentArgs by handle.navArgs()

    data class State(
        val account: GoogleAccount
    )

    val state = syncManager.getConnectorById<GDriveAppDataConnector>(navArgs.identifier)
        .map {
            State(it.account)
        }
        .catch {
            if (it is NoSuchElementException) popNavStack() else throw it
        }
        .asLiveData2()

    fun forceSync() = launch(appScope) {
        log(TAG) { "forceSync()" }
        syncManager.sync(navArgs.identifier)
        popNavStack()
    }

    fun disconnct() = launch(appScope) {
        log(TAG) { "disconnct()" }
        syncManager.disconnect(navArgs.identifier)
        popNavStack()
    }

    fun reset() = launch(appScope) {
        log(TAG) { "reset()" }
        syncManager.resetData(navArgs.identifier)
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "Actions", "Fragment", "VM")
    }
}