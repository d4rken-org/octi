package eu.darken.octi.modules.power.core

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.power.PowerModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    powerSerializer: PowerSerializer,
    moshi: Moshi,
) : BaseModuleCache<PowerInfo>(
    moduleId = PowerModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = powerSerializer,
    moshi = moshi,
) {

    companion object {
        val TAG = logTag("Module", "Power", "Cache")
    }
}