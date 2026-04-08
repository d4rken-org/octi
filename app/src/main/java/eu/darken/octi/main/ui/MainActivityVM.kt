package eu.darken.octi.main.ui

import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.SingleEventFlow
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

    init {
        log(_tag) { "init()" }
    }

    /**
     * Parse a widget-deeplink intent and emit it to [deeplinkEvents].
     *
     * Returns `true` if the intent was a valid, accepted deeplink. Returns `false` otherwise
     * (wrong action, missing extras, invalid module type, or onboarding not done).
     */
    fun handleDeeplinkIntent(intent: Intent?): Boolean {
        val parsed = WidgetDeeplink.parse(intent) ?: return false

        if (!generalSettings.isOnboardingDone.valueBlocking) {
            log(_tag, WARN) { "Dropping widget deeplink: onboarding not done" }
            return false
        }

        log(_tag, INFO) { "Widget deeplink accepted: device=${parsed.deviceId} module=${parsed.moduleType}" }
        deeplinkEvents.tryEmit(parsed)
        return true
    }
}
