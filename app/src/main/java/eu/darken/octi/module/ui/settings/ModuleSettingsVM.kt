package eu.darken.octi.module.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.modules.apps.core.AppsSettings
import eu.darken.octi.modules.clipboard.ClipboardSettings
import eu.darken.octi.modules.connectivity.core.ConnectivitySettings
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.modules.wifi.core.WifiSettings
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class ModuleSettingsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    private val powerSettings: PowerSettings,
    private val connectivitySettings: ConnectivitySettings,
    private val wifiSettings: WifiSettings,
    private val appsSettings: AppsSettings,
    private val clipboardSettings: ClipboardSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val isPro: Boolean,
        val isPowerEnabled: Boolean,
        val isConnectivityEnabled: Boolean,
        val isWifiEnabled: Boolean,
        val isAppsEnabled: Boolean,
        val isAppsInstallerEnabled: Boolean,
        val isClipboardEnabled: Boolean,
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
            clipboardSettings.isEnabled.flow,
        ) { apps, installer, clipboard ->
            Triple(apps, installer, clipboard)
        },
    ) { upgradeInfo, (power, connectivity, wifi), (apps, installer, clipboard) ->
        State(
            isPro = upgradeInfo.isPro,
            isPowerEnabled = power,
            isConnectivityEnabled = connectivity,
            isWifiEnabled = wifi,
            isAppsEnabled = apps,
            isAppsInstallerEnabled = installer,
            isClipboardEnabled = clipboard,
        )
    }.shareLatest(scope = vmScope)

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
        appsSettings.includeInstaller.value(enabled)
    }

    fun setClipboardEnabled(enabled: Boolean) = launch {
        clipboardSettings.isEnabled.value(enabled)
    }

    companion object {
        private val TAG = logTag("Settings", "Module", "VM")
    }
}
