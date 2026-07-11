package eu.darken.octi.main.ui.onboarding.connect

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.main.core.GeneralSettings
import javax.inject.Inject

@HiltViewModel
class ConnectVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel4(dispatcherProvider) {

    private var finished = false

    fun finishWithSyncSetup() {
        if (finished) return
        finished = true
        log(TAG) { "finishWithSyncSetup()" }
        launch {
            try {
                generalSettings.isOnboardingDone.value(true)
            } catch (e: Exception) {
                finished = false
                throw e
            }
            navTo(Nav.Main.Dashboard, popUpTo = Nav.Main.Welcome, inclusive = true)
            navTo(Nav.Sync.List)
        }
    }

    fun finishToDashboard() {
        if (finished) return
        finished = true
        log(TAG) { "finishToDashboard()" }
        launch {
            try {
                generalSettings.isOnboardingDone.value(true)
            } catch (e: Exception) {
                finished = false
                throw e
            }
            navTo(Nav.Main.Dashboard, popUpTo = Nav.Main.Welcome, inclusive = true)
        }
    }

    companion object {
        private val TAG = logTag("Onboarding", "Connect", "VM")
    }
}
