package eu.darken.octi.modules.meta.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.meta.MetaModule
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    metaSerializer: MetaSerializer,
    json: Json,
) : BaseModuleCache<MetaInfo>(
    moduleId = MetaModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = metaSerializer,
    json = json,
) {

    companion object {
        val TAG = logTag("Module", "Meta", "Cache")
    }
}
