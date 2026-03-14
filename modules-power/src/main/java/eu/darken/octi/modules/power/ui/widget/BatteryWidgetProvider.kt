package eu.darken.octi.modules.power.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.LocalSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

class BatteryGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        log(TAG, VERBOSE) { "provideGlance(id=$id)" }

        val ep = EntryPointAccessors.fromApplication(context, BatteryWidgetEntryPoint::class.java)
        val metaRepo = ep.metaRepo()
        val powerRepo = ep.powerRepo()

        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to get appWidgetId: ${e.asLog()}" }
            AppWidgetManager.INVALID_APPWIDGET_ID
        }

        provideContent {
            val widgetOptions = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            val themeColors = WidgetTheme.parseThemeColors(widgetOptions)

            val metaState = metaRepo.state.collectAsState(initial = null)
            val powerState = powerRepo.state.collectAsState(initial = null)

            val heightDp = LocalSize.current.height.value
            val maxRows = if (heightDp > 0) maxOf(1, ((heightDp - 16) / 32).toInt()) else Int.MAX_VALUE

            BatteryWidgetContent(
                metaState = metaState.value,
                powerState = powerState.value,
                themeColors = themeColors,
                maxRows = maxRows,
            )
        }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Glance")
    }
}

class BatteryWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = BatteryGlanceWidget()

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Provider")
    }
}
