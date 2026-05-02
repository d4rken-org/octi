package eu.darken.octi.main.ui.settings.general

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.theming.ThemeColor
import eu.darken.octi.common.theming.ThemeMode
import eu.darken.octi.common.theming.ThemeStyle
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.core.updater.UpdateChecker
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
    private val updateChecker: UpdateChecker,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val isUpdateCheckSupported: Boolean,
        val isUpdateCheckEnabled: Boolean,
        val themeMode: ThemeMode,
        val themeStyle: ThemeStyle,
        val themeColor: ThemeColor,
    )

    val state = combine(
        upgradeRepo.upgradeInfo,
        flow { emit(updateChecker.isCheckSupported()) },
        generalSettings.isUpdateCheckEnabled.flow,
        generalSettings.themeMode.flow,
        combine(
            generalSettings.themeStyle.flow,
            generalSettings.themeColor.flow,
        ) { style, color -> style to color },
    ) { upgrade, isUpdateCheckSupported, isUpdateCheckEnabled, themeMode, (themeStyle, themeColor) ->
        State(
            isPro = upgrade.isPro,
            isUpdateCheckSupported = isUpdateCheckSupported,
            isUpdateCheckEnabled = isUpdateCheckEnabled,
            themeMode = themeMode,
            themeStyle = themeStyle,
            themeColor = themeColor,
        )
    }.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = launch {
        if (!requirePro()) return@launch
        generalSettings.themeMode.value(mode)
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        if (!requirePro()) return@launch
        generalSettings.themeStyle.value(style)
    }

    fun setThemeColor(color: ThemeColor) = launch {
        if (!requirePro()) return@launch
        generalSettings.themeColor.value(color)
    }

    fun goUpgrade() = navTo(Nav.Main.Upgrade())

    fun setUpdateCheckEnabled(enabled: Boolean) = launch {
        generalSettings.isUpdateCheckEnabled.value(enabled)
    }

    private suspend fun requirePro(): Boolean {
        if (upgradeRepo.isPro()) return true
        navTo(Nav.Main.Upgrade())
        return false
    }

    companion object {
        private val TAG = logTag("Settings", "General", "VM")
    }
}
