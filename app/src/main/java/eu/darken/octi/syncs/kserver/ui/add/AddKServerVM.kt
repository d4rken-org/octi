package eu.darken.octi.syncs.kserver.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.syncs.kserver.core.KServer
import eu.darken.octi.syncs.kserver.core.KServerAccountRepo
import eu.darken.octi.syncs.kserver.core.KServerEndpoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddKServerVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val kServerAccountRepo: KServerAccountRepo,
    private val kServerEndpointFactory: KServerEndpoint.Factory,
    private val syncSettings: SyncSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val serverType: KServer.Official = KServer.Official.PROD,
        val isBusy: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asLiveData2()

    fun selectType(type: KServer.Official) {
        log(TAG) { "selectType(type=$type)" }
        _state.value = _state.value.copy(serverType = type)
    }

    fun createAccount() = launch {
        log(TAG) { "createAccount()" }
        _state.value = _state.value.copy(isBusy = true)
        try {
            val type = _state.value.serverType.address
            log(TAG) { "createAccount(): $type" }
            val endpoint = kServerEndpointFactory.create(type)

            withContext(NonCancellable) {
                log(TAG) { "Creating account..." }
                val newCredentials = endpoint.createNewAccount()
                log(TAG, INFO) { "New account created: $newCredentials" }
                kServerAccountRepo.add(newCredentials)
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
            AddKServerFragmentDirections.actionAddKServerFragmentToKServerLinkClientFragment().navigate()
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Add", "KServer", "Fragment", "VM")
    }
}