package eu.darken.octi.syncs.octiserver.ui.link.host

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.withPrevious
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.execute
import eu.darken.octi.syncs.octiserver.core.OctiServerConnector
import eu.darken.octi.syncs.octiserver.ui.link.OctiServerLinkOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class OctiServerLinkHostVM @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val syncSettings: SyncSettings,
    private val json: Json,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val connectorIdFlow = MutableStateFlow<String?>(null)
    private val stateLock = Mutex()

    data class State(
        val encodedLinkCode: String? = null,
        val linkOption: OctiServerLinkOption = OctiServerLinkOption.QRCODE,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val connectorFlow = connectorIdFlow
        .filterNotNull()
        .flatMapLatest { idStr ->
            syncManager.allConnectors.map { connectors ->
                connectors.single { it.identifier.idString == idStr } as OctiServerConnector
            }
        }

    var deviceLinkedEvents = SingleEventFlow<Unit>()
        private set

    val linkCodeCopiedEvents = SingleEventFlow<Unit>()

    private var deviceMonitorJob: Job? = null

    fun initialize(connectorId: String) {
        if (connectorIdFlow.value != null) return
        connectorIdFlow.value = connectorId

        launch {
            val connector = connectorFlow.first()
            syncSettings.pausedConnectorIds
                .map { it.contains(connector.identifier) }
                .distinctUntilChanged()
                .collectLatest { isPaused ->
                    if (isPaused) {
                        log(TAG, WARN) { "initialize(): connector ${connector.identifier} is paused, navigating up" }
                        navUp()
                        return@collectLatest
                    }
                    while (currentCoroutineContext().isActive) {
                        connector.execute(ConnectorCommand.Sync(SyncOptions(writeData = false)))
                        delay(3.seconds)
                    }
                }
        }
    }

    fun onScreenVisible() {
        log(TAG) { "onScreenVisible()" }

        // Cancel previous monitor and discard stale events
        deviceMonitorJob?.cancel()
        deviceLinkedEvents = SingleEventFlow()

        // Reset to loading state, then generate fresh code
        launch {
            stateLock.withLock {
                _state.value = _state.value.copy(encodedLinkCode = null)
            }
            val connector = connectorFlow.first()
            if (syncSettings.isPaused(connector.identifier)) {
                log(TAG, WARN) { "onScreenVisible(): connector ${connector.identifier} is paused, navigating up" }
                navUp()
                return@launch
            }
            val container = connector.createLinkCode()
            log(TAG) { "New magic link code generated." }
            stateLock.withLock {
                _state.value = _state.value.copy(encodedLinkCode = container.toEncodedString(json))
            }
        }

        // Monitor for new device linking
        deviceMonitorJob = vmScope.launch {
            connectorFlow
                .flatMapLatest { it.state }
                .map { it.deviceMetadata }
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
    }

    fun onLinkOptionSelected(option: OctiServerLinkOption) = launch {
        log(TAG) { "onLinkOptionSelected(option=$option)" }
        stateLock.withLock {
            _state.value = _state.value.copy(linkOption = option)
        }
    }

    /**
     * A TV rarely has a share target that can accept text, and the code is ~500 characters, so
     * "read it off the screen and retype it" is not a viable fallback. Copy is the escape hatch.
     */
    fun copyLinkCode(context: Context) = launch {
        log(TAG) { "copyLinkCode()" }
        val encodedCode = _state.value.encodedLinkCode ?: return@launch
        // On Main deliberately: launch{} runs on Dispatchers.Default, and resolving CLIPBOARD_SERVICE
        // off the main thread throws "Can't create handler inside thread that has not called
        // Looper.prepare()" on some versions. ClipboardHelper carries a workaround for the same trap.
        withContext(dispatcherProvider.Main) {
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@withContext
            clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, encodedCode))
        }
        // Android 13+ shows its own clipboard confirmation, a toast on top of it would double up.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) linkCodeCopiedEvents.tryEmit(Unit)
    }

    fun shareLinkCode(activity: Activity) = launch {
        log(TAG) { "shareLinkCode()" }
        val encodedCode = _state.value.encodedLinkCode ?: return@launch
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, encodedCode)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, "Octi - Link device")
        activity.startActivity(shareIntent)
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "Link", "Host", "VM")
        private const val CLIP_LABEL = "Octi link code"
    }
}
