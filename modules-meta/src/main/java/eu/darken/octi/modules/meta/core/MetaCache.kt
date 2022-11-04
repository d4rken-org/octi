package eu.darken.octi.modules.meta.core

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.meta.MetaModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    metaSerializer: MetaSerializer,
    moshi: Moshi,
) : BaseModuleCache<MetaInfo>(
    moduleId = MetaModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = metaSerializer,
    moshi = moshi,
) {

    companion object {
        val TAG = logTag("Module", "Meta", "Cache")
    }
}