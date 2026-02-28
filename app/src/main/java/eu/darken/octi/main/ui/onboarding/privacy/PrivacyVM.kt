package eu.darken.octi.main.ui.onboarding.privacy

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.PrivacyPolicy
import eu.darken.octi.common.WebpageTool
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.updater.UpdateChecker
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PrivacyVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val updateChecker: UpdateChecker,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isUpdateCheckSupported: Boolean,
        val isUpdateCheckEnabled: Boolean,
    )

    val state = generalSettings.isUpdateCheckEnabled.flow.map {
        State(
            isUpdateCheckEnabled = it,
            isUpdateCheckSupported = updateChecker.isCheckSupported(),
        )
    }.shareLatest(scope = vmScope)

    fun openPrivacyPolicy() {
        webpageTool.open(PrivacyPolicy.URL)
    }

    fun finishScreen() = launch {
        generalSettings.isOnboardingDone.value(true)
        navTo(Nav.Main.Dashboard, popUpTo = Nav.Main.Welcome, inclusive = true)
    }

    fun toggleUpdateCheck() = launch {
        log(TAG) { "toggleUpdateCheck()" }
        generalSettings.isUpdateCheckEnabled.value(!generalSettings.isUpdateCheckEnabled.value())
    }

    companion object {
        private val TAG = logTag("Onboarding", "Privacy", "VM")
    }
}
