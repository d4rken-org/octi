package eu.darken.octi.syncs.kserver.ui.link.host

import android.app.Activity
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.flow.withPrevious
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.syncs.kserver.core.KServerConnector
import eu.darken.octi.syncs.kserver.ui.link.KServerLinkOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class KServerLinkHostVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val json: Json,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val connectorIdFlow = MutableStateFlow<String?>(null)
    private val stateLock = Mutex()

    data class State(
        val encodedLinkCode: String? = null,
        val linkOption: KServerLinkOption = KServerLinkOption.QRCODE,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.shareLatest(scope = vmScope)

    private val connectorFlow = connectorIdFlow
        .filterNotNull()
        .flatMapLatest { idStr ->
            syncManager.connectors.map { connectors ->
                connectors.single { it.identifier.idString == idStr } as KServerConnector
            }
        }

    val deviceLinkedEvents = SingleEventFlow<Unit>()

    fun initialize(connectorId: String) {
        if (connectorIdFlow.value != null) return
        connectorIdFlow.value = connectorId

        launch {
            connectorFlow
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
                .first()
            deviceLinkedEvents.tryEmit(Unit)
        }

        launch {
            val connector = connectorFlow.first()
            val container = connector.createLinkCode()
            log(TAG) { "New magic link code generated." }

            stateLock.withLock {
                _state.value = _state.value.copy(encodedLinkCode = container.toEncodedString(json))
            }
        }
        launch {
            val connector = connectorFlow.first()
            while (currentCoroutineContext().isActive) {
                connector.sync(SyncOptions())
                delay(3000)
            }
        }
    }

    fun onLinkOptionSelected(option: KServerLinkOption) = launch {
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
        private val TAG = logTag("Sync", "KServer", "Link", "Host", "VM")
    }
}
