package eu.darken.octi.main.ui.onboarding.welcome

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import javax.inject.Inject

@HiltViewModel
class WelcomeVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
) : ViewModel4(dispatcherProvider) {

    fun finishScreen() {
        navTo(Nav.Main.Privacy)
    }

    companion object {
        private val TAG = logTag("Onboarding", "Welcome", "VM")
    }
}
