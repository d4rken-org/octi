package eu.darken.octi.modules.clipboard

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleRepo
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    appsSettings: ClipboardSettings,
    appsInfoSource: ClipboardHandler,
    appsSync: ClipboardSync,
    appsCache: ClipboardCache,
) : BaseModuleRepo<ClipboardInfo>(
    tag = TAG,
    moduleId = ClipboardModule.MODULE_ID,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = appsSettings,
    infoSource = appsInfoSource,
    moduleSync = appsSync,
    moduleCache = appsCache,
) {

    companion object {
        val TAG = logTag("Module", "Clipboard", "Repo")
    }
}