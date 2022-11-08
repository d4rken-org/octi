package eu.darken.octi.modules.clipboard

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleCache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardCache @Inject constructor(
    @ApplicationContext private val context: Context,
    dispatcherProvider: DispatcherProvider,
    appsSerializer: ClipboardSerializer,
    moshi: Moshi,
) : BaseModuleCache<ClipboardItem>(
    moduleId = ClipboardModule.MODULE_ID,
    tag = TAG,
    dispatcherProvider = dispatcherProvider,
    context = context,
    moduleSerializer = appsSerializer,
    moshi = moshi,
) {

    companion object {
        val TAG = logTag("Module", "Clipboard", "Cache")
    }
}