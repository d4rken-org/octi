package eu.darken.octi.main.ui

import android.content.Intent
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.navigation.FileShareDeeplink
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.navigation.WidgetDeeplink
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.themeStateBlocking
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.files.core.IncomingShareInbox
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val fileShareSettings: FileShareSettings,
    private val incomingShareInbox: IncomingShareInbox,
    private val syncSettings: SyncSettings,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val themeState = generalSettings.themeState.stateIn(
        vmScope,
        SharingStarted.Eagerly,
        generalSettings.themeStateBlocking,
    )

    val startDestination: NavigationDestination
        get() = if (generalSettings.isOnboardingDone.valueBlocking) {
            Nav.Main.Dashboard
        } else {
            Nav.Main.Welcome
        }

    /**
     * One-shot widget deeplink events. Channel-backed, buffered — events emitted before a
     * collector attaches (e.g. cold-start) wait in the channel. Equal values in quick succession
     * are not deduplicated (unlike a StateFlow), so rapid double-taps still produce two emissions.
     */
    val deeplinkEvents = SingleEventFlow<WidgetDeeplink.OpenModuleDetail>()

    /**
     * One-shot file-share notification deeplink events. Same buffering semantics as
     * [deeplinkEvents] — the dashboard reads it and navigates to the unified file-share list
     * with the sender device pre-filtered.
     */
    val fileShareDeeplinkEvents = SingleEventFlow<FileShareDeeplink.OpenFileShare>()

    /**
     * One-shot system-share-sheet events ([Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE]).
     * MainActivity observes this and either navigates to FileShareList with the inbox token, or
     * shows a snackbar for the unsupported / module-disabled cases.
     */
    val incomingShareEvents = SingleEventFlow<IncomingShareEvent>()

    sealed interface IncomingShareEvent {
        data class IncomingShare(val token: String, val selfDeviceId: String) : IncomingShareEvent
        data object Unsupported : IncomingShareEvent
        data object ModuleDisabled : IncomingShareEvent
    }

    init {
        log(_tag) { "init()" }
    }

    /**
     * Parse a widget, file-share, or system-share intent and emit the matching event. Returns
     * `true` if the intent was recognised and consumed (meaning callers should typically reset
     * the navigation back to the relevant landing screen). Returns `false` otherwise.
     */
    fun handleDeeplinkIntent(intent: Intent?): Boolean {
        if (intent != null && (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE)) {
            return handleSystemShareIntent(intent)
        }

        FileShareDeeplink.parse(intent)?.let { parsed ->
            if (!generalSettings.isOnboardingDone.valueBlocking) {
                log(_tag, WARN) { "Dropping file-share deeplink: onboarding not done" }
                return false
            }
            log(_tag, INFO) { "File-share deeplink accepted: device=${parsed.deviceId}" }
            fileShareDeeplinkEvents.tryEmit(parsed)
            return true
        }

        val widget = WidgetDeeplink.parse(intent) ?: return false
        if (!generalSettings.isOnboardingDone.valueBlocking) {
            log(_tag, WARN) { "Dropping widget deeplink: onboarding not done" }
            return false
        }
        log(_tag, INFO) { "Widget deeplink accepted: device=${widget.deviceId} module=${widget.moduleType}" }
        deeplinkEvents.tryEmit(widget)
        return true
    }

    /**
     * Returns `true` only when the intent triggers navigation (the IncomingShare path). The
     * Unsupported and ModuleDisabled paths emit a toast and return `false` so MainActivity does
     * not pop the user back to Dashboard — they should stay where they were.
     */
    private fun handleSystemShareIntent(intent: Intent): Boolean {
        if (!generalSettings.isOnboardingDone.valueBlocking) {
            log(_tag, WARN) { "Dropping system-share intent: onboarding not done" }
            return false
        }
        val uris = extractStreamUris(intent)
        if (uris.isEmpty()) {
            log(_tag, INFO) { "System-share intent had no usable stream URIs" }
            incomingShareEvents.tryEmit(IncomingShareEvent.Unsupported)
            return false
        }
        if (!fileShareSettings.isEnabled.valueBlocking) {
            log(_tag, INFO) { "System-share intent dropped: file-share module disabled" }
            incomingShareEvents.tryEmit(IncomingShareEvent.ModuleDisabled)
            return false
        }
        val token = incomingShareInbox.enqueue(uris)
        log(_tag, INFO) { "System-share intent accepted: ${uris.size} URI(s), token=$token" }
        incomingShareEvents.tryEmit(
            IncomingShareEvent.IncomingShare(token = token, selfDeviceId = syncSettings.deviceId.id),
        )
        return true
    }

    companion object {
        /**
         * Pull URIs out of `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intents in a way that handles
         * the three real-world delivery shapes: the typed `EXTRA_STREAM` extras (single + array)
         * and the `clipData` fallback some apps use. Filters to `content://` and `file://` only,
         * dedupes while preserving first-occurrence order.
         */
        fun extractStreamUris(intent: Intent): List<Uri> {
            val collected = mutableListOf<Uri>()
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { collected += it }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        ?.filterNotNull()
                        ?.let { collected += it }
                }
            }
            intent.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { collected += it }
                }
            }
            return collected
                .filter { it.scheme == "content" || it.scheme == "file" }
                .distinct()
        }
    }
}
