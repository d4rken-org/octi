package eu.darken.octi.modules.apps.core

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.apps.AppsModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    appsSerializer: AppsSerializer,
    moshi: Moshi,
) : BaseModuleCache<AppsInfo>(
    moduleId = AppsModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = appsSerializer,
    moshi = moshi,
) {

    companion object {
        val TAG = logTag("Module", "Apps", "Cache")
    }
}