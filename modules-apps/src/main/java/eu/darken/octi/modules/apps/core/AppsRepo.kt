package eu.darken.octi.modules.apps.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.apps.AppsModule
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    appsSettings: AppsSettings,
    appsInfoSource: AppsInfoSource,
    appsSync: AppsSync,
) : BaseModuleRepo<AppsInfo>(
    tag = TAG,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = appsSettings,
    infoSource = appsInfoSource,
    moduleSync = appsSync,
) {

    override val moduleId: ModuleId = AppsModule.MODULE_ID

    companion object {
        val TAG = logTag("Module", "Apps", "Repo")
    }
}