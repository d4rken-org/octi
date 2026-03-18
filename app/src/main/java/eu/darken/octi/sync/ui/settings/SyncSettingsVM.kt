package eu.darken.octi.sync.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.isPro
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.common.flow.combine
import javax.inject.Inject

@HiltViewModel
class SyncSettingsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val showDashboardCard: Boolean,
        val deviceLabel: String?,
        val backgroundSyncEnabled: Boolean,
        val backgroundSyncInterval: Int,
        val backgroundSyncOnMobile: Boolean,
        val isPro: Boolean,
        val backgroundSyncChargingEnabled: Boolean,
        val backgroundSyncChargingInterval: Int,
        val foregroundSyncEnabled: Boolean,
        val foregroundSyncInterval: Int,
    )

    val state = combine(
        syncSettings.showDashboardCard.flow,
        syncSettings.deviceLabel.flow,
        syncSettings.backgroundSyncEnabled.flow,
        syncSettings.backgroundSyncInterval.flow,
        syncSettings.backgroundSyncOnMobile.flow,
        upgradeRepo.upgradeInfo,
        syncSettings.backgroundSyncChargingEnabled.flow,
        syncSettings.backgroundSyncChargingInterval.flow,
        syncSettings.foregroundSyncEnabled.flow,
        syncSettings.foregroundSyncInterval.flow,
    ) { showCard, deviceLabel, bgEnabled, bgInterval, bgMobile, upgradeInfo, chargingEnabled, chargingInterval, fgEnabled, fgInterval ->
        State(
            showDashboardCard = showCard,
            deviceLabel = deviceLabel,
            backgroundSyncEnabled = bgEnabled,
            backgroundSyncInterval = bgInterval,
            backgroundSyncOnMobile = bgMobile,
            isPro = upgradeInfo.isPro,
            backgroundSyncChargingEnabled = chargingEnabled,
            backgroundSyncChargingInterval = chargingInterval,
            foregroundSyncEnabled = fgEnabled,
            foregroundSyncInterval = fgInterval,
        )
    }.asStateFlow()

    fun setShowDashboardCard(enabled: Boolean) = launch {
        syncSettings.showDashboardCard.value(enabled)
    }

    fun setDeviceLabel(label: String?) = launch {
        syncSettings.deviceLabel.value(label?.ifBlank { null })
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) = launch {
        syncSettings.backgroundSyncEnabled.value(enabled)
    }

    fun setBackgroundSyncInterval(minutes: Int) = launch {
        syncSettings.backgroundSyncInterval.value(minutes.coerceIn(15, 2880))
    }

    fun setBackgroundSyncOnMobile(enabled: Boolean) = launch {
        syncSettings.backgroundSyncOnMobile.value(enabled)
    }

    fun setChargingEnabled(enabled: Boolean) = launch {
        if (!requirePro()) return@launch
        syncSettings.backgroundSyncChargingEnabled.value(enabled)
    }

    fun setChargingInterval(minutes: Int) = launch {
        if (!requirePro()) return@launch
        syncSettings.backgroundSyncChargingInterval.value(minutes.coerceIn(15, 2880))
    }

    fun setForegroundSyncEnabled(enabled: Boolean) = launch {
        if (!requirePro()) return@launch
        syncSettings.foregroundSyncEnabled.value(enabled)
    }

    fun setForegroundSyncInterval(minutes: Int) = launch {
        if (!requirePro()) return@launch
        syncSettings.foregroundSyncInterval.value(minutes.coerceIn(5, 60))
    }

    private suspend fun requirePro(): Boolean {
        if (upgradeRepo.isPro()) return true
        navTo(Nav.Main.Upgrade())
        return false
    }

    companion object {
        private val TAG = logTag("Settings", "Sync", "VM")
    }
}
