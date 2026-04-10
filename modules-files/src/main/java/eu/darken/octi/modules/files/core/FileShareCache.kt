package eu.darken.octi.modules.files.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import eu.darken.octi.modules.files.FileShareModule
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileShareCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    fileShareSerializer: FileShareSerializer,
    json: Json,
) : BaseModuleCache<FileShareInfo>(
    moduleId = FileShareModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = fileShareSerializer,
    json = json,
) {

    companion object {
        val TAG = logTag("Module", "Files", "Cache")
    }
}
