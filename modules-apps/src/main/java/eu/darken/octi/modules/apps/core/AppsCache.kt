package eu.darken.octi.modules.apps.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.apps.AppsModule
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    appsSerializer: AppsSerializer,
    json: Json,
) : BaseModuleCache<AppsInfo>(
    moduleId = AppsModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = appsSerializer,
    json = json,
) {

    companion object {
        val TAG = logTag("Module", "Apps", "Cache")
    }
}
