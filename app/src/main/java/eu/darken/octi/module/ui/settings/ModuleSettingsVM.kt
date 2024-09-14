package eu.darken.octi.module.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.modules.apps.core.AppsSettings
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class ModuleSettingsVM @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    appsSettings: AppsSettings,
) : ViewModel3(dispatcherProvider) {


    val state = combine(
        upgradeRepo.upgradeInfo,
        appsSettings.isEnabled.flow,
    ) { upgradeInfo, isAppsEnabled ->
        State(
            isPro = upgradeInfo.isPro,
            isAppModuleEnabled = isAppsEnabled,
        )
    }.asLiveData()

    data class State(
        val isPro: Boolean,
        val isAppModuleEnabled: Boolean,
    )

    companion object {
        private val TAG = logTag("Settings", "Module", "VM")
    }
}