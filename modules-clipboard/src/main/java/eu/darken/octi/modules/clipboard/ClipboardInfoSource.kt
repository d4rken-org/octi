package eu.darken.octi.modules.clipboard

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.module.core.ModuleInfoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardInfoSource @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) : ModuleInfoSource<ClipboardInfo> {

    private val currentClipboard = MutableStateFlow(ClipboardInfo())

    override val info: Flow<ClipboardInfo> = currentClipboard
        .setupCommonEventHandlers(TAG) { "info" }
        .replayingShare(appScope)

    suspend fun setText(text: String) {
        log(TAG) { "setText(text=$text)" }
        currentClipboard.value = ClipboardInfo(
            data = text.toByteString(),
            type = ClipboardInfo.Type.SIMPLE_TEXT,
        )
    }

    companion object {
        internal val TAG = logTag("Module", "Clipboard", "InfoSource")
    }
}