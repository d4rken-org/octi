package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileShareSync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    fileShareSerializer: FileShareSerializer,
) : BaseModuleSync<FileShareInfo>(
    tag = TAG,
    moduleId = FileShareModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = fileShareSerializer,
) {

    companion object {
        val TAG = logTag("Module", "Files", "Sync")
    }
}
