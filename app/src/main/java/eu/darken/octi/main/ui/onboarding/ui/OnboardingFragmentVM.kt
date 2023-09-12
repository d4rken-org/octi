package eu.darken.octi.main.ui.onboarding.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.main.core.GeneralSettings
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class OnboardingFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun finishOnboarding() = launch {
        generalSettings.isOnboardingDone.value(true)
        OnboardingFragmentDirections.actionOnboardingFragmentToDashFragment().navigate()
    }

    companion object {
        val TAG = logTag("Onboarding", "Fragment", "VM")
    }
}