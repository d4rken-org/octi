package eu.darken.octi.syncs.jserver.ui.link.client

import androidx.lifecycle.SavedStateHandle
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.syncs.jserver.core.JServer
import eu.darken.octi.syncs.jserver.core.JServerAccountRepo
import eu.darken.octi.syncs.jserver.core.JServerEndpoint
import eu.darken.octi.syncs.jserver.core.LinkCodeContainer
import eu.darken.octi.syncs.jserver.ui.link.LinkOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class JServerLinkClientVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val moshi: Moshi,
    private val syncSettings: SyncSettings,
    private val jServerEndpointFactory: JServerEndpoint.Factory,
    private val jServerAccountRepo: JServerAccountRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val stateLock = Mutex()

    data class State(
        val encodedLinkCode: String? = null,
        val linkOption: LinkOption = LinkOption.DIRECT,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asLiveData2()

    fun onLinkOptionSelected(option: LinkOption) = launch {
        log(TAG) { "onLinkOptionSelected(option=$option)" }
        stateLock.withLock {
            _state.value = _state.value.copy(linkOption = option)
        }
    }

    fun onCodeEntered(rawCode: String) = launch {
        log(TAG) { "onCodeEntered(rawCode=$rawCode)" }
        val linkContainer = LinkCodeContainer.fromEncodedString(moshi, rawCode)
        log(TAG) { "Got container: $linkContainer" }

        val endpoint = jServerEndpointFactory.create(linkContainer.serverAdress)

        val tempCredentials = JServer.Credentials(
            serverAdress = linkContainer.serverAdress,
            accountId = linkContainer.accountId,
            deviceId = syncSettings.deviceId,
            devicePassword = JServer.Credentials.DevicePassword(linkContainer.linkCode.code),
        )
        endpoint.setCredentials(tempCredentials)
        val newAccount = endpoint.createAccount(syncSettings.deviceId)
        log(TAG) { "Account successfully linked: $newAccount" }
        jServerAccountRepo.add(newAccount)
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Link", "Client", "Fragment", "VM")
    }
}