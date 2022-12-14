package eu.darken.octi.modules.wifi.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.modules.wifi.WifiModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiSync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    wifiSerializer: WifiSerializer,
) : BaseModuleSync<WifiInfo>(
    tag = TAG,
    moduleId = WifiModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = wifiSerializer,
) {

    companion object {
        val TAG = logTag("Module", "Wifi", "Sync")
    }
}