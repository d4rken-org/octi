package eu.darken.octi.main.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationDestination
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

    init {
        log(_tag) { "init()" }
    }
}
