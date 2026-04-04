package eu.darken.octi.modules.clipboard.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.clipboard.ClipboardInfo
import kotlinx.coroutines.flow.first

class CopyClipboardCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val deviceId = parameters[KEY_DEVICE_ID] ?: return
        log(TAG, INFO) { "CopyClipboardCallback: deviceId=$deviceId" }

        try {
            val ep = EntryPointAccessors.fromApplication(context, ClipboardWidgetEntryPoint::class.java)
            val clipboardRepo = ep.clipboardRepo()
            val clipboardHandler = ep.clipboardHandler()

            val state = clipboardRepo.state.first()
            @Suppress("UNCHECKED_CAST")
            val allData = state.all as? Collection<eu.darken.octi.module.core.ModuleData<ClipboardInfo>>
            val deviceData = allData?.firstOrNull { it.deviceId.id.toString() == deviceId }
            val info = deviceData?.data ?: return

            clipboardHandler.setOSClipboard(info)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to copy clipboard: ${e.asLog()}" }
        }

        ClipboardGlanceWidget().updateAll(context)
    }

    companion object {
        private val TAG = logTag("Module", "Clipboard", "Widget", "Copy")
        val KEY_DEVICE_ID = ActionParameters.Key<String>("deviceId")
    }
}

class PasteClipboardCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        log(TAG, INFO) { "PasteClipboardCallback triggered" }

        try {
            val ep = EntryPointAccessors.fromApplication(context, ClipboardWidgetEntryPoint::class.java)
            val clipboardHandler = ep.clipboardHandler()
            clipboardHandler.shareCurrentOSClipboard()
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to paste clipboard: ${e.asLog()}" }
        }

        ClipboardGlanceWidget().updateAll(context)
    }

    companion object {
        private val TAG = logTag("Module", "Clipboard", "Widget", "Paste")
    }
}
