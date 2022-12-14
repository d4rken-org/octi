package eu.darken.octi.modules.apps.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.modules.apps.AppsModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsSync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    appsSerializer: AppsSerializer,
) : BaseModuleSync<AppsInfo>(
    tag = TAG,
    moduleId = AppsModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = appsSerializer,
) {

    companion object {
        val TAG = logTag("Module", "Apps", "Sync")
    }
}