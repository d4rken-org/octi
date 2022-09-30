package eu.darken.octi.modules.power.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.power.PowerModule
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    powerSettings: PowerSettings,
    powerInfoSource: PowerInfoSource,
    powerSync: PowerSync,
) : eu.darken.octi.module.core.BaseModuleRepo<PowerInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = powerSettings,
    infoSource = powerInfoSource,
    moduleSync = powerSync,
) {

    override val moduleId: ModuleId = PowerModule.MODULE_ID

    companion object {
        val TAG = logTag("Module", "Power", "Repo")
    }
}