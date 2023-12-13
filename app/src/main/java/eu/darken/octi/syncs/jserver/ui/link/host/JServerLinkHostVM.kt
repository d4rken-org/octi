package eu.darken.octi.syncs.jserver.ui.link.host

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.withPrevious
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.getConnectorById
import eu.darken.octi.syncs.jserver.core.JServerConnector
import eu.darken.octi.syncs.jserver.ui.link.JServerLinkOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class JServerLinkHostVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val moshi: Moshi
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: JServerLinkHostFragmentArgs by handle.navArgs()
    private val stateLock = Mutex()

    data class State(
        val encodedLinkCode: String? = null,
        val linkOption: JServerLinkOption = JServerLinkOption.QRCODE,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asLiveData2()

    val autoNavOnNewDevice = syncManager
        .getConnectorById<JServerConnector>(navArgs.identifier)
        .flatMapLatest { it.state }
        .map { it.devices }
        .withPrevious()
        .map { (old, new) ->
            if (old == null) return@map null
            if (new == null) return@map null
            if (new.size <= old.size) return@map null
            Unit
        }
        .filterNotNull()
        .asLiveData2()

    init {
        launch {
            val connector = syncManager.getConnectorById<JServerConnector>(navArgs.identifier).first()
            val container = connector.createLinkCode()
            log(TAG) { "New magic link code generated." }
            handle["code"] = container

            stateLock.withLock {
                _state.value = _state.value.copy(encodedLinkCode = container.toEncodedString(moshi))
            }
        }
        launch {
            val connector = syncManager.getConnectorById<JServerConnector>(navArgs.identifier).first()
            while (currentCoroutineContext().isActive) {
                connector.sync(SyncOptions())
                delay(3000)
            }
        }
    }

    fun onLinkOptionSelected(option: JServerLinkOption) = launch {
        log(TAG) { "onLinkOptionSelected(option=$option)" }
        stateLock.withLock {
            _state.value = _state.value.copy(linkOption = option)
        }
    }

    fun shareLinkCode(activity: Activity) = launch {
        log(TAG) { "shareLinkCode()" }
        val encodedCode = _state.value.encodedLinkCode!!
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, encodedCode)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Octi - Link device")
        activity.startActivity(shareIntent)
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Link", "Host", "Fragment", "VM")
    }
}