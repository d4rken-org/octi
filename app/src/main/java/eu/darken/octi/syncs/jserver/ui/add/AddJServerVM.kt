package eu.darken.octi.syncs.jserver.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.syncs.jserver.core.JServer
import eu.darken.octi.syncs.jserver.core.JServerAccountRepo
import eu.darken.octi.syncs.jserver.core.JServerEndpoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddJServerVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val jServerAccountRepo: JServerAccountRepo,
    private val jServerEndpointFactory: JServerEndpoint.Factory,
    private val syncSettings: SyncSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val serverType: JServer.Official = JServer.Official.GRYLLS,
        val isBusy: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asLiveData2()

    fun selectType(type: JServer.Official) {
        log(TAG) { "selectType(type=$type)" }
        _state.value = _state.value.copy(serverType = type)
    }

    fun createAccount() = launch {
        log(TAG) { "createAccount()" }
        _state.value = _state.value.copy(isBusy = true)
        try {
            val type = _state.value.serverType.address
            log(TAG) { "createAccount(): $type" }
            val endpoint = jServerEndpointFactory.create(type)

            withContext(NonCancellable) {
                log(TAG) { "Creating account..." }
                val newCredentials = endpoint.createAccount(syncSettings.deviceId)
                log(TAG, INFO) { "New account created: $newCredentials" }
                jServerAccountRepo.add(newCredentials)
            }
            navEvents.postValue(null)

        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    fun linkAccount() = launch {
        log(TAG) { "linkAccount()" }
        _state.value = _state.value.copy(isBusy = true)
        try {
            AddJServerFragmentDirections.actionAddJServerFragmentToJServerLinkClientFragment().navigate()
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Add", "JServer", "Fragment", "VM")
    }
}