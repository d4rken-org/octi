package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.modules.files.FileShareModule
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileShareRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    fileShareSettings: FileShareSettings,
    fileShareHandler: FileShareHandler,
    fileShareSync: FileShareSync,
    fileShareCache: FileShareCache,
) : BaseModuleRepo<FileShareInfo>(
    tag = TAG,
    moduleId = FileShareModule.MODULE_ID,
    scope = scope,
    dispatcherProvider = dispatcherProvider,
    moduleSettings = fileShareSettings,
    infoSource = fileShareHandler,
    moduleSync = fileShareSync,
    moduleCache = fileShareCache,
) {

    companion object {
        val TAG = logTag("Module", "Files", "Repo")
    }
}
