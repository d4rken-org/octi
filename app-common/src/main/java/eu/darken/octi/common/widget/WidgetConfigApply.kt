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
 * Persists [newConfig] into the per-instance options bundle, sets [ComponentActivity.RESULT_OK],
 * finishes the activity immediately, and refreshes the widget via [updateWidget] on the
 * process-level lifecycle scope so the Glance recomposition runs to completion regardless of
 * activity destruction.
 *
 * If [newConfig] is in custom-theme mode but missing colours, the activity is finished
 * without applying — mirrors the existing per-activity guard.
 *
 * **Important**: [updateWidget] runs on a scope that outlives the activity. Make sure the
 * lambda passed in does not capture the activity (use the application context).
 */
fun ComponentActivity.applyWidgetConfig(
    appWidgetId: Int,
    newConfig: WidgetInstanceConfig,
    tag: String,
    updateWidget: suspend () -> Unit,
) {
    if (!newConfig.isMaterialYou && (newConfig.customBg == null || newConfig.customAccent == null)) {
        finish()
        return
    }

    val widgetManager = AppWidgetManager.getInstance(this)
    val options = widgetManager.getAppWidgetOptions(appWidgetId)
    WidgetInstanceConfig.write(options, newConfig)
    widgetManager.updateAppWidgetOptions(appWidgetId, options)

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
