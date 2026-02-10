package eu.darken.octi.modules.connectivity.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.modules.connectivity.ConnectivityModule
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    connectivitySettings: ConnectivitySettings,
    connectivityInfoSource: ConnectivityInfoSource,
    connectivitySync: ConnectivitySync,
    connectivityCache: ConnectivityCache,
) : BaseModuleRepo<ConnectivityInfo>(
    tag = TAG,
    moduleId = ConnectivityModule.MODULE_ID,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = connectivitySettings,
    infoSource = connectivityInfoSource,
    moduleSync = connectivitySync,
    moduleCache = connectivityCache,
) {

    companion object {
        val TAG = logTag("Module", "Connectivity", "Repo")
    }
}
