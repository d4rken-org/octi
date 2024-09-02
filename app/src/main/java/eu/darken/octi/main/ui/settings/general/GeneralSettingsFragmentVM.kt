package eu.darken.octi.main.ui.settings.general

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsFragmentVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    data class State(
        val isPro: Boolean
    )


    val state = upgradeRepo.upgradeInfo.map {
        State(
            isPro = it.isPro,
        )
    }.asLiveData2()

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}