package eu.darken.octi.main.ui.onboarding.welcome

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import javax.inject.Inject

@HiltViewModel
class WelcomeFragmentVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun finishScreen() = launch {
        WelcomeFragmentDirections.actionWelcomeFragmentToPrivacyFragment().navigate()
    }

    companion object {
        val TAG = logTag("Onboarding", "Welcome", "Fragment", "VM")
    }
}