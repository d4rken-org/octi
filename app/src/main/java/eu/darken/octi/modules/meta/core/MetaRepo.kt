package eu.darken.octi.modules.meta.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.power.core.PowerRepo
import eu.darken.octi.sync.core.BaseModuleRepo
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : BaseModuleRepo<MetaInfo>(
    tag = PowerRepo.TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider
) {

    companion object {
        val TAG = logTag("Module", "Meta", "Repo")
    }
}