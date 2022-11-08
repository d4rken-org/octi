package eu.darken.octi.modules.clipboard

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardSync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    appsSerializer: ClipboardSerializer,
) : BaseModuleSync<ClipboardItem>(
    tag = TAG,
    moduleId = ClipboardModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = appsSerializer,
) {

    companion object {
        val TAG = logTag("Module", "Clipboard", "Sync")
    }
}