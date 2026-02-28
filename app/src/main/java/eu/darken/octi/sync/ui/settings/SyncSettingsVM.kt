package eu.darken.octi.sync.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class SyncSettingsVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val deviceLabel: String?,
        val backgroundSyncEnabled: Boolean,
        val backgroundSyncInterval: Int,
        val backgroundSyncOnMobile: Boolean,
    )

    val state = combine(
        syncSettings.deviceLabel.flow,
        syncSettings.backgroundSyncEnabled.flow,
        syncSettings.backgroundSyncInterval.flow,
        syncSettings.backgroundSyncOnMobile.flow,
    ) { deviceLabel, bgEnabled, bgInterval, bgMobile ->
        State(
            deviceLabel = deviceLabel,
            backgroundSyncEnabled = bgEnabled,
            backgroundSyncInterval = bgInterval,
            backgroundSyncOnMobile = bgMobile,
        )
    }.shareLatest(scope = vmScope)

    fun setDeviceLabel(label: String?) = launch {
        syncSettings.deviceLabel.value(label?.ifBlank { null })
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) = launch {
        syncSettings.backgroundSyncEnabled.value(enabled)
    }

    fun setBackgroundSyncInterval(minutes: Int) = launch {
        syncSettings.backgroundSyncInterval.value(minutes)
    }

    fun setBackgroundSyncOnMobile(enabled: Boolean) = launch {
        syncSettings.backgroundSyncOnMobile.value(enabled)
    }

    companion object {
        private val TAG = logTag("Settings", "Sync", "VM")
    }
}
