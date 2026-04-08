package eu.darken.octi.modules.clipboard.ui.widget

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.clipboard.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Swallows a row click without performing any action. Used to prevent clicks on non-copyable
 * rows from bubbling up to the widget container's open-app handler.
 */
class NoOpClickCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // intentionally empty
    }
}

class CopyClipboardCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val deviceId = parameters[KEY_DEVICE_ID] ?: return
        log(TAG, VERBOSE) { "CopyClipboardCallback: deviceId=$deviceId" }

        var copied = false
        try {
            val ep = EntryPointAccessors.fromApplication(context, ClipboardWidgetEntryPoint::class.java)
            val clipboardRepo = ep.clipboardRepo()
            val clipboardHandler = ep.clipboardHandler()

            val state = clipboardRepo.state.first()
            val info = state.all.firstOrNull { it.deviceId.id == deviceId }?.data ?: return

            clipboardHandler.setOSClipboard(info)
            copied = true
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to copy clipboard: ${e.asLog()}" }
        }

        if (copied && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.module_clipboard_widget_copied_toast),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        ClipboardGlanceWidget().updateAll(context)
    }

    companion object {
        private val TAG = logTag("Module", "Clipboard", "Widget", "Copy")
        val KEY_DEVICE_ID = ActionParameters.Key<String>("deviceId")
    }
}

