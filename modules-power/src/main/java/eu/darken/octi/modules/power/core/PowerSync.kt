package eu.darken.octi.modules.power.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerSync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    powerSerializer: PowerSerializer,
) : BaseModuleSync<PowerInfo>(
    tag = TAG,
    moduleId = PowerModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = powerSerializer,
) {

    companion object {
        val TAG = logTag("Module", "Power", "Sync")
    }
}