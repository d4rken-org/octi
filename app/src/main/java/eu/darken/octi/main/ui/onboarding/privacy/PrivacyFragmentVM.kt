package eu.darken.octi.main.ui.onboarding.privacy

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.main.core.GeneralSettings
import javax.inject.Inject

@HiltViewModel
class PrivacyFragmentVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun finishScreen() = launch {
        generalSettings.isOnboardingDone.value(true)
        PrivacyFragmentDirections.actionPrivacyFragmentToDashFragment().navigate()
    }

    companion object {
        val TAG = logTag("Onboarding", "Privacy", "Fragment", "VM")
    }
}