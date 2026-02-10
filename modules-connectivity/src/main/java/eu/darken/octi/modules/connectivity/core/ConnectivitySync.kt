package eu.darken.octi.modules.connectivity.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.modules.connectivity.ConnectivityModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivitySync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    connectivitySerializer: ConnectivitySerializer,
) : BaseModuleSync<ConnectivityInfo>(
    tag = TAG,
    moduleId = ConnectivityModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = connectivitySerializer,
) {

    companion object {
        val TAG = logTag("Module", "Connectivity", "Sync")
    }
}
