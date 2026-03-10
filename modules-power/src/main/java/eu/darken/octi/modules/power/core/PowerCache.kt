package eu.darken.octi.modules.power.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.power.PowerModule
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    powerSerializer: PowerSerializer,
    json: Json,
) : BaseModuleCache<PowerInfo>(
    moduleId = PowerModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = powerSerializer,
    json = json,
) {

    companion object {
        val TAG = logTag("Module", "Power", "Cache")
    }
}
