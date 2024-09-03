package eu.darken.octi.main.ui.onboarding.privacy

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.updater.UpdateChecker
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PrivacyFragmentVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val updateChecker: UpdateChecker,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val isUpdateCheckSupported: Boolean,
        val isUpdateCheckEnabled: Boolean,
    )

    val state = generalSettings.isUpdateCheckEnabled.flow.map {
        State(
            isUpdateCheckEnabled = it,
            isUpdateCheckSupported = updateChecker.isCheckSupported(),
        )
    }.asLiveData2()

    fun finishScreen() = launch {
        generalSettings.isOnboardingDone.value(true)
        PrivacyFragmentDirections.actionPrivacyFragmentToDashFragment().navigate()
    }

    fun toggleUpdateCheck() = launch {
        log(TAG) { "toggleUpdateCheck()" }
        generalSettings.isUpdateCheckEnabled.value(!generalSettings.isUpdateCheckEnabled.value())
    }

    companion object {
        val TAG = logTag("Onboarding", "Privacy", "Fragment", "VM")
    }
}