package eu.darken.octi.syncs.kserver.ui.actions

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.livedata.SingleLiveEvent
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.getConnectorById
import eu.darken.octi.syncs.kserver.core.KServer
import eu.darken.octi.syncs.kserver.core.KServerConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class KServerActionsVM @Inject constructor(
    handle: SavedStateHandle,
    @AppScope private val appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val syncSettings: SyncSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: KServerActionsFragmentArgs by handle.navArgs()

    val actionEvents = SingleLiveEvent<ActionEvents>()

    data class State(
        val credentials: KServer.Credentials,
        val isPaused: Boolean,
    )

    val state = combine(
        syncManager.getConnectorById<KServerConnector>(navArgs.identifier),
        syncSettings.pausedConnectors.flow.map { it.contains(navArgs.identifier) },
    ) { connector, paused ->
        State(
            connector.credentials,
            isPaused = paused,
        )
    }
        .catch {
            if (it is NoSuchElementException) navEvents.postValue(null)
            else throw it
        }
        .asLiveData2()

    fun togglePause() = launch {
        log(TAG) { "togglePause()" }
        syncManager.togglePause(navArgs.identifier)
    }

    fun linkNewDevice() {
        log(TAG) { "linkNewDevice()" }
        KServerActionsFragmentDirections.actionKServerActionsFragmentToKServerLinkFragment(
            navArgs.identifier
        ).navigate()
    }

    fun forceSync() = launch(appScope) {
        log(TAG) { "forceSync()" }
        syncManager.sync(navArgs.identifier)
        popNavStack()
    }

    fun disconnct() = launch(appScope) {
        log(TAG) { "disconnct()" }
        syncManager.disconnect(navArgs.identifier)
        navEvents.postValue(null)
    }

    fun reset() = launch(appScope) {
        log(TAG) { "reset()" }
        syncManager.resetData(navArgs.identifier)
        navEvents.postValue(null)
    }

    companion object {
        private val TAG = logTag("Sync", "KServer", "Actions", "Fragment", "VM")
    }
}