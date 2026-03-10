package eu.darken.octi.modules.wifi.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.wifi.WifiModule
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    wifiSerializer: WifiSerializer,
    json: Json,
) : BaseModuleCache<WifiInfo>(
    moduleId = WifiModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = wifiSerializer,
    json = json,
) {

    companion object {
        val TAG = logTag("Module", "Wifi", "Cache")
    }
}
