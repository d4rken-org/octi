package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.modules.meta.MetaModule
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    metaSettings: MetaSettings,
    metaInfoSource: MetaInfoSource,
    metaSync: MetaSync,
    metaCache: MetaCache,
) : BaseModuleRepo<MetaInfo>(
    tag = TAG,
    moduleId = MetaModule.MODULE_ID,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = metaSettings,
    infoSource = metaInfoSource,
    moduleSync = metaSync,
    moduleCache = metaCache,
) {

    companion object {
        val TAG = logTag("Module", "Meta", "Repo")
    }
}