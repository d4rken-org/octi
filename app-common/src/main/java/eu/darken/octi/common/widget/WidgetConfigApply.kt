package eu.darken.octi.common.widget

import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import kotlinx.coroutines.launch

/**
 * Persists [newConfig] via [widgetSettings] before finishing the activity, so a process kill
 * after `setResult` cannot drop the user's input. The widget refresh ([updateWidget]) runs on
 * the process-level lifecycle scope so it completes regardless of activity destruction.
 *
 * If [newConfig] is in custom-theme mode but missing colours, the activity is finished
 * without applying — mirrors the existing per-activity guard.
 *
 * **Important**: [updateWidget] runs on a scope that outlives the activity. Make sure the
 * lambda passed in does not capture the activity (use the application context).
 */
suspend fun ComponentActivity.applyWidgetConfig(
    appWidgetId: Int,
    newConfig: WidgetInstanceConfig,
    widgetSettings: WidgetSettings,
    tag: String,
    updateWidget: suspend () -> Unit,
) {
    if (!newConfig.isMaterialYou && (newConfig.customBg == null || newConfig.customAccent == null)) {
        finish()
        return
    }

    try {
        widgetSettings.update(appWidgetId, newConfig)
    } catch (e: Exception) {
        log(tag, ERROR) { "Failed to persist widget config: ${e.asLog()}" }
        finish()
        return
    }

    setResult(
        RESULT_OK,
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
    )
    finish()

    ProcessLifecycleOwner.get().lifecycleScope.launch {
        try {
            updateWidget()
        } catch (e: Exception) {
            log(tag, ERROR) { "Failed to update widget: ${e.asLog()}" }
        }
    }
}
