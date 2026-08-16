package eu.darken.octi.syncs.octiserver.ui.link.client

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.syncs.octiserver.core.OctiServerHub
import eu.darken.octi.syncs.octiserver.core.LinkingData
import eu.darken.octi.syncs.octiserver.core.linkCodeShape
import eu.darken.octi.syncs.octiserver.ui.link.OctiServerLinkOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class OctiServerLinkClientVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val json: Json,
    private val kServerHub: OctiServerHub,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val stateLock = Mutex()

    data class State(
        val encodedLinkCode: String? = null,
        val linkOption: OctiServerLinkOption = OctiServerLinkOption.QRCODE,
        val isBusy: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun onLinkOptionSelected(option: OctiServerLinkOption) = launch {
        log(TAG) { "onLinkOptionSelected(option=$option)" }
        stateLock.withLock {
            _state.value = _state.value.copy(linkOption = option)
        }
    }

    fun onCodeEntered(rawCode: String) = launch {
        // Never log the code itself: it carries the account's payload encryption keyset and the
        // server link credential, and debug recordings are shared with support. This fingerprint is
        // enough to tell truncation from a typo from a wrong-format paste.
        log(TAG) { "onCodeEntered(${rawCode.trim().linkCodeShape()})" }
        _state.value = _state.value.copy(isBusy = true)
        try {
            // Server address only. Logging the whole container pulled in the redacted-but-still
            // key-derived prefixes that LinkCode and KeySet put in their toString().
            val linkContainer = LinkingData.fromEncodedString(json, rawCode).also {
                log(TAG) { "Decoded link code for ${it.serverAdress.address}" }
            }

            kServerHub.linkAcount(linkContainer)

            popTo(Nav.Sync.List)
        } finally {
            _state.value = _state.value.copy(isBusy = false)
        }
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "Link", "Client", "VM")
    }
}
