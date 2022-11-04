package eu.darken.octi.modules.wifi.core

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.wifi.WifiModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    wifiSerializer: WifiSerializer,
    moshi: Moshi,
) : BaseModuleCache<WifiInfo>(
    moduleId = WifiModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = wifiSerializer,
    moshi = moshi,
) {

    companion object {
        val TAG = logTag("Module", "Wifi", "Cache")
    }
}