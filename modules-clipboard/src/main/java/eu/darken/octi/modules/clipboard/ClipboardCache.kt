package eu.darken.octi.modules.clipboard

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    clipboardSerializer: ClipboardSerializer,
    json: Json,
) : BaseModuleCache<ClipboardInfo>(
    moduleId = ClipboardModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = clipboardSerializer,
    json = json,
) {

    companion object {
        val TAG = logTag("Module", "Clipboard", "Cache")
    }
}
