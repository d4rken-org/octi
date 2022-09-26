package eu.darken.octi.modules.wifi.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.BaseModuleRepo
import eu.darken.octi.modules.ModuleId
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.wifi.WifiModule
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    wifiSettings: WifiSettings,
    wifiInfoSource: WifiInfoSource,
    wifiSync: WifiSync,
) : BaseModuleRepo<WifiInfo>(
    tag = MetaRepo.TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = wifiSettings,
    infoSource = wifiInfoSource,
    moduleSync = wifiSync,
) {

    override val moduleId: ModuleId = WifiModule.MODULE_ID

    companion object {
        val TAG = logTag("Module", "Wifi", "Repo")
    }
}