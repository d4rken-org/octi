package eu.darken.octi.syncs.jserver.ui.link.client

import androidx.lifecycle.SavedStateHandle
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.syncs.jserver.core.JServerHub
import eu.darken.octi.syncs.jserver.core.LinkingData
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
    private val jServerHub: JServerHub,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val stateLock = Mutex()

    data class State(
        val encodedLinkCode: String? = null,
        val linkOption: LinkOption = LinkOption.DIRECT,
        val isBusy: Boolean = false,
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
        _state.value = _state.value.copy(isBusy = true)
        try {
            val linkContainer = LinkingData.fromEncodedString(moshi, rawCode).also {
                log(TAG) { "Got container: $it" }
            }

            jServerHub.linkAcount(linkContainer)

            popNavStack()
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Link", "Client", "Fragment", "VM")
    }
}