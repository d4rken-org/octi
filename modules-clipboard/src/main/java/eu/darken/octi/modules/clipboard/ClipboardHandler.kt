package eu.darken.octi.modules.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
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
class ClipboardHandler @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) : ModuleInfoSource<ClipboardItem> {

    private val currentClipboard = MutableStateFlow(ClipboardItem())

    private val cm by lazy { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    override val info: Flow<ClipboardItem> = currentClipboard
        .setupCommonEventHandlers(TAG) { "info" }
        .replayingShare(appScope)

    fun setSharedText(text: String) {
        log(TAG) { "setSharedClipboard(text=$text)" }
        val info = ClipboardItem(
            data = text.toByteString(),
            type = ClipboardItem.Type.SIMPLE_TEXT,
        )
        setSharedClipboard(info)
    }

    fun setSharedClipboard(data: ClipData) {
        log(TAG) { "setSharedClipboard(data=$data)" }
        val info = data.toClipboardInfo()
        setSharedClipboard(info ?: ClipboardItem())
    }

    fun setSharedClipboard(info: ClipboardItem) {
        log(TAG) { "setSharedClipboard(info=$info)" }
        currentClipboard.value = info
    }

    private fun ClipboardItem.toClipData(): ClipData? = when (type) {
        ClipboardItem.Type.EMPTY -> null
        ClipboardItem.Type.SIMPLE_TEXT -> ClipData(
            "Simple text",
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
            ClipData.Item(data.utf8())
        )
    }

    private fun ClipData.toClipboardInfo(): ClipboardItem? = when {
        itemCount > 0 -> ClipboardItem(
            type = ClipboardItem.Type.SIMPLE_TEXT,
            data = getItemAt(0).text.toString().toByteString(),
        )
        else -> {
            log(TAG, WARN) { "Failed to convert: $this" }
            null
        }
    }

    fun setOSClipboard(info: ClipboardItem?) {
        log(TAG) { "setOSClipboard(info=$info)" }
        val data = info?.toClipData()
        if (data != null) {
            cm.setPrimaryClip(data)
        } else {
            cm.clearPrimaryClip()
        }
    }

    fun shareCurrentOSClipboard() {
        log(TAG) { "shareCurrentOSClipboard()" }
        val info = if (cm.hasPrimaryClip()) {
            cm.primaryClip?.toClipboardInfo()
        } else {
            null
        }

        log(TAG) { "shareCurrentOSClipboard(): $info" }
        setSharedClipboard(info ?: ClipboardItem())
    }

    companion object {
        internal val TAG = logTag("Module", "Clipboard", "InfoSource")
    }
}