package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.BaseModuleRepo
import eu.darken.octi.modules.ModuleId
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : BaseModuleRepo<MetaInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider
) {

    override val moduleId: ModuleId = MetaModule.MODULE_ID

    companion object {
        val TAG = logTag("Module", "Meta", "Repo")
    }
}