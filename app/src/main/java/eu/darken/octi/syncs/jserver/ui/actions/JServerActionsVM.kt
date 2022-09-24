package eu.darken.octi.syncs.jserver.ui.actions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.getConnectorById
import eu.darken.octi.syncs.jserver.core.JServer
import eu.darken.octi.syncs.jserver.core.JServerConnector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class JServerActionsVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: JServerActionsFragmentArgs by handle.navArgs()

    data class State(
        val credentials: JServer.Credentials
    )

    val state = syncManager.getConnectorById<JServerConnector>(navArgs.identifier)
        .map {
            State(it.credentials)
        }
        .catch {
            if (it is NoSuchElementException) navEvents.postValue(null)
            else throw it
        }
        .asLiveData2()

    fun linkNewDevice() {
        log(TAG) { "linkNewDevice()" }
        JServerActionsFragmentDirections.actionJServerActionsFragmentToJServerLinkFragment(
            navArgs.identifier
        ).navigate()
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

    fun forceSync() = launch {
        log(TAG) { "forceSync()" }
        syncManager.triggerSync(navArgs.identifier, stats = true, readData = true, writeData = true)
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Actions", "Fragment", "VM")
    }
}