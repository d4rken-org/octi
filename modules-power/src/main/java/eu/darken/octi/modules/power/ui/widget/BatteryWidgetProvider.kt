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
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.widget.WidgetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class BatteryGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        log(TAG, VERBOSE) { "provideGlance(id=$id)" }

        val ep = EntryPointAccessors.fromApplication(context, BatteryWidgetEntryPoint::class.java)
        val metaRepo = ep.metaRepo()
        val powerRepo = ep.powerRepo()
        val widgetSettings = ep.widgetSettings()

        val appWidgetId = try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to get appWidgetId: ${e.asLog()}" }
            AppWidgetManager.INVALID_APPWIDGET_ID
        }

        val initialConfig = widgetSettings.configValue(appWidgetId) {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        }

        provideContent {
            val instanceConfig = widgetSettings.config(appWidgetId)
                .collectAsState(initial = initialConfig).value
            val themeColors = instanceConfig.themeColors
            val allowedDeviceIds = instanceConfig.allowedDeviceIds.takeIf { it.isNotEmpty() }

            val metaState = metaRepo.state.collectAsState(initial = null)
            val powerState = powerRepo.state.collectAsState(initial = null)

            val heightDp = LocalSize.current.height.value
            val maxRows = if (heightDp > 0) maxOf(1, ((heightDp - 16) / 32).toInt()) else Int.MAX_VALUE

            BatteryWidgetContent(
                metaState = metaState.value,
                powerState = powerState.value,
                themeColors = themeColors,
                maxRows = maxRows,
                allowedDeviceIds = allowedDeviceIds,
            )
        }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Glance")
    }
}

@AndroidEntryPoint
class BatteryWidgetProvider : GlanceAppWidgetReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var widgetSettings: WidgetSettings

    override val glanceAppWidget: GlanceAppWidget = BatteryGlanceWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // GlanceAppWidgetReceiver.onDeleted already calls goAsync() internally; calling it
        // again here returns null. AppScope keeps the coroutine alive while Glance's own
        // pending result holds the process up.
        appScope.launch {
            try {
                widgetSettings.delete(appWidgetIds)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to delete widget settings: ${e.asLog()}" }
            }
        }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Provider")
    }
}
