package eu.darken.octi.modules.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.collections.toByteString
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.module.core.ModuleInfoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import okio.ByteString.Companion.decodeBase64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHandler @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val settings: ClipboardSettings,
    private val serializer: ClipboardSerializer,
) : ModuleInfoSource<ClipboardInfo> {

    private val currentClipboard = DynamicStateFlow<ClipboardInfo>(TAG, appScope) {
        try {
            settings.lastClipboard.value()?.decodeBase64()?.let { serializer.deserialize(it) } ?: ClipboardInfo()
        } catch (e: Exception) {
            ClipboardInfo()
        }
    }

    private val cm by lazy { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    override val info: Flow<ClipboardInfo> = currentClipboard.flow
        .setupCommonEventHandlers(TAG) { "info" }
        .replayingShare(appScope)

    suspend fun setSharedClipboard(info: ClipboardInfo) {
        log(TAG) { "setSharedClipboard(info=$info)" }
        currentClipboard.updateBlocking { info }
        settings.lastClipboard.value(serializer.serialize(info).base64())
    }

    suspend fun setOSClipboard(info: ClipboardInfo) {
        log(TAG) { "setOSClipboard(info=$info)" }
        if (info.type != ClipboardInfo.Type.EMPTY) {
            info.toClipData()?.let { cm.setPrimaryClip(it) }
        } else {
            cm.clearPrimaryClip()
        }
    }

    suspend fun shareCurrentOSClipboard() {
        log(TAG) { "shareCurrentOSClipboard()" }
        val info = if (cm.hasPrimaryClip()) {
            cm.primaryClip?.toClipboardInfo()
        } else {
            null
        }

        log(TAG) { "shareCurrentOSClipboard(): $info" }
        setSharedClipboard(info ?: ClipboardInfo())
    }

    private fun ClipboardInfo.toClipData(): ClipData? = when (type) {
        ClipboardInfo.Type.EMPTY -> null
        ClipboardInfo.Type.SIMPLE_TEXT -> ClipData(
            "Simple text",
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
            ClipData.Item(data.utf8())
        )
    }

    private fun ClipData.toClipboardInfo(): ClipboardInfo? = when {
        itemCount > 0 -> ClipboardInfo(
            type = ClipboardInfo.Type.SIMPLE_TEXT,
            data = getItemAt(0).text.toString().toByteString(),
        )
        else -> {
            log(TAG, WARN) { "Failed to convert: $this" }
            null
        }
    }

    companion object {
        internal val TAG = logTag("Module", "Clipboard", "InfoSource")
    }
}