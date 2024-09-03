package eu.darken.octi.main.ui.settings.general

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.main.core.updater.UpdateChecker
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsFragmentVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val updateChecker: UpdateChecker,
) : ViewModel3(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val isUpdateCheckSupported: Boolean,
    )


    val state = combine(
        upgradeRepo.upgradeInfo,
        flow { emit(updateChecker.isCheckSupported()) },
    ) { upgrade, isUpdateCheckSupported ->
        State(
            isPro = upgrade.isPro,
            isUpdateCheckSupported = isUpdateCheckSupported,
        )
    }.asLiveData2()

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}