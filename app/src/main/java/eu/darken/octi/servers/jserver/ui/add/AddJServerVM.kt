package eu.darken.octi.servers.jserver.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.servers.jserver.core.JServer
import eu.darken.octi.servers.jserver.core.JServerAccountRepo
import eu.darken.octi.servers.jserver.core.JServerEndpoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class AddJServerVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val jServerAccountRepo: JServerAccountRepo,
    private val jServerEndpointFactory: JServerEndpoint.Factory,
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

    fun createAccount() = launch(context = dispatcherProvider.IO + NonCancellable) {
        val type = _state.value.serverType.address
        log(TAG) { "createAccount(): $type" }
        val endpoint = jServerEndpointFactory.create(type)

        log(TAG) { "Creating account..." }
        val newCredentials = endpoint.createNewAccount()
        log(TAG, INFO) { "New account created: $newCredentials" }
        jServerAccountRepo.add(newCredentials)
    }

    fun linkAccount(
        accountId: JServer.Credentials.AccountId,
        shareCode: JServer.Credentials.ShareCode
    ) = launch(context = dispatcherProvider.IO + NonCancellable) {
        val type = _state.value.serverType.address
        log(TAG) { "linkAccount(): $type" }
        val endpoint = jServerEndpointFactory.create(type)

        log(TAG) { "Linking account..." }
        val newCredentials = endpoint.linkToAccount(accountId, shareCode)
        log(TAG, INFO) { "Linked account created: $newCredentials" }
        jServerAccountRepo.add(newCredentials)
    }

    companion object {
        private val TAG = logTag("Sync", "Add", "JServer", "Fragment", "VM")
    }
}