package eu.darken.octi.modules.clipboard.ui.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

/**
 * Glance click callback that routes a non-Pro Clipboard widget tile to the upgrade screen.
 */
class ClipboardUpgradeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        log(TAG, VERBOSE) { "onAction()" }
        try {
            val ep = EntryPointAccessors.fromApplication(context, ClipboardWidgetEntryPoint::class.java)
            ep.upgradeLauncher().launch(context)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to launch upgrade: ${e.asLog()}" }
        }
    }

    companion object {
        private val TAG = logTag("Module", "Clipboard", "Widget", "UpgradeAction")
    }
}
