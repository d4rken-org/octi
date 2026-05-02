package eu.darken.octi.module.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.modules.apps.core.AppsSettings
import eu.darken.octi.modules.clipboard.ClipboardSettings
import eu.darken.octi.modules.connectivity.core.ConnectivitySettings
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.modules.wifi.core.WifiSettings
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class ModuleSettingsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepo,
    private val powerSettings: PowerSettings,
    private val connectivitySettings: ConnectivitySettings,
    private val wifiSettings: WifiSettings,
    private val appsSettings: AppsSettings,
    private val clipboardSettings: ClipboardSettings,
    private val fileShareSettings: FileShareSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val isPowerEnabled: Boolean,
        val isConnectivityEnabled: Boolean,
        val isWifiEnabled: Boolean,
        val isAppsEnabled: Boolean,
        val isAppsInstallerEnabled: Boolean,
        val isClipboardEnabled: Boolean,
        val isFilesEnabled: Boolean,
    )

    val state = combine(
        upgradeRepo.upgradeInfo,
        combine(
            powerSettings.isEnabled.flow,
            connectivitySettings.isEnabled.flow,
            wifiSettings.isEnabled.flow,
        ) { power, connectivity, wifi ->
            Triple(power, connectivity, wifi)
        },
        combine(
            appsSettings.isEnabled.flow,
            appsSettings.includeInstaller.flow,
        ) { apps, installer ->
            apps to installer
        },
        clipboardSettings.isEnabled.flow,
        fileShareSettings.isEnabled.flow,
    ) { upgradeInfo, (power, connectivity, wifi), (apps, installer), clipboard, files ->
        State(
            isPro = upgradeInfo.isPro,
            isPowerEnabled = power,
            isConnectivityEnabled = connectivity,
            isWifiEnabled = wifi,
            isAppsEnabled = apps,
            isAppsInstallerEnabled = installer,
            isClipboardEnabled = clipboard,
            isFilesEnabled = files,
        )
    }.asStateFlow()

    fun setPowerEnabled(enabled: Boolean) = launch {
        powerSettings.isEnabled.value(enabled)
    }

    fun setConnectivityEnabled(enabled: Boolean) = launch {
        connectivitySettings.isEnabled.value(enabled)
    }

    fun setWifiEnabled(enabled: Boolean) = launch {
        wifiSettings.isEnabled.value(enabled)
    }

    fun setAppsEnabled(enabled: Boolean) = launch {
        appsSettings.isEnabled.value(enabled)
    }

    fun setAppsInstallerEnabled(enabled: Boolean) = launch {
        if (!requirePro()) return@launch
        appsSettings.includeInstaller.value(enabled)
    }

    fun setClipboardEnabled(enabled: Boolean) = launch {
        clipboardSettings.isEnabled.value(enabled)
    }

    fun setFilesEnabled(enabled: Boolean) = launch {
        fileShareSettings.isEnabled.value(enabled)
    }

    private suspend fun requirePro(): Boolean {
        if (upgradeRepo.isPro()) return true
        navTo(Nav.Main.Upgrade())
        return false
    }

    companion object {
        private val TAG = logTag("Settings", "Module", "VM")
    }
}
