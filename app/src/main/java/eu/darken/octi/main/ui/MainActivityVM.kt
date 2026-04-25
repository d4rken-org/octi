package eu.darken.octi.main.ui

import android.content.Intent
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject


@HiltViewModel
class MainActivityVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
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

    init {
        log(_tag) { "init()" }
    }

    /**
     * Parse a widget or file-share deeplink intent and emit it. Returns `true` if a deeplink was
     * recognised and accepted. Returns `false` otherwise (wrong action, missing extras, or
     * onboarding not done).
     */
    fun handleDeeplinkIntent(intent: Intent?): Boolean {
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
}
