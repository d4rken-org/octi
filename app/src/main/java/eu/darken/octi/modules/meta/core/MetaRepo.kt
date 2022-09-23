package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.BaseModuleRepo
import eu.darken.octi.modules.ModuleId
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
) : BaseModuleRepo<MetaInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = metaSettings,
    infoSource = metaInfoSource,
    moduleSync = metaSync,
) {

    override val moduleId: ModuleId = MetaModule.MODULE_ID

    companion object {
        val TAG = logTag("Module", "Meta", "Repo")
    }
}