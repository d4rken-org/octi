package eu.darken.octi.syncs.octiserver.ui.add

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.syncs.octiserver.core.OctiServer
import eu.darken.octi.syncs.octiserver.core.OctiServerAccountRepo
import eu.darken.octi.syncs.octiserver.core.OctiServerEndpoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddOctiServerVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val kServerAccountRepo: OctiServerAccountRepo,
    private val kServerEndpointFactory: OctiServerEndpoint.Factory,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    data class State(
        val serverType: OctiServer.Official? = OctiServer.Official.PROD,
        val useLegacyEncryption: Boolean = false,
        val isBusy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun selectType(type: OctiServer.Official?) {
        log(TAG) { "selectType(type=$type)" }
        _state.value = _state.value.copy(serverType = type)
    }

    fun toggleLegacyEncryption() {
        _state.value = _state.value.copy(useLegacyEncryption = !_state.value.useLegacyEncryption)
    }

    private fun parseCustomServer(raw: String): OctiServer.Address {
        val regex = Regex("([a-zA-Z]+)://([a-zA-Z0-9.-]+):(\\d+)")
        val matchResult = regex.find(raw) ?: throw IllegalArgumentException("Invalid address input")

        val (protocol, address, port) = matchResult.destructured
        return OctiServer.Address(
            domain = address,
            protocol = protocol,
            port = port.toInt()
        )
    }

    fun createAccount(customServer: String?) = launch {
        log(TAG) { "createAccount($customServer)" }
        _state.value = _state.value.copy(isBusy = true)
        try {
            val address = _state.value.serverType?.address ?: parseCustomServer(customServer!!)
            log(TAG) { "createAccount(): $address" }
            val endpoint = kServerEndpointFactory.create(address)

            withContext(NonCancellable) {
                log(TAG) { "Creating account..." }
                val newCredentials = endpoint.createNewAccount(
                    useLegacyEncryption = _state.value.useLegacyEncryption,
                )
                log(TAG, INFO) { "New account created: $newCredentials" }
                kServerAccountRepo.add(newCredentials)
            }
            popTo(Nav.Sync.List)
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    fun linkAccount() = launch {
        log(TAG) { "linkAccount()" }
        _state.value = _state.value.copy(isBusy = true)
        try {
            navTo(Nav.Sync.OctiServerLinkClient)
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Add", "OctiServer", "VM")
    }
}