package eu.darken.octi.modules.connectivity.ui.widget

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

class NetworkGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        log(TAG, VERBOSE) { "provideGlance(id=$id)" }

        val ep = EntryPointAccessors.fromApplication(context, NetworkWidgetEntryPoint::class.java)
        val metaRepo = ep.metaRepo()
        val connectivityRepo = ep.connectivityRepo()
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
            val connectivityState = connectivityRepo.state.collectAsState(initial = null)

            val size = LocalSize.current
            val heightDp = size.height.value
            val widthDp = size.width.value
            val maxRows = if (heightDp > 0) {
                val available = heightDp - NetworkWidgetSizing.FIXED_OVERHEAD_DP
                if (widthDp >= NetworkWidgetSizing.TWO_COLUMN_MIN_WIDTH_DP) {
                    // Two tiles per grid row; fits 0 if the widget is shorter than one tile.
                    maxOf(0, (available / NetworkWidgetSizing.TILE_SLOT_DP).toInt()) * 2
                } else {
                    maxOf(0, (available / NetworkWidgetSizing.ROW_SLOT_DP).toInt())
                }
            } else {
                Int.MAX_VALUE
            }

            NetworkWidgetContent(
                metaState = metaState.value,
                connectivityState = connectivityState.value,
                themeColors = themeColors,
                maxRows = maxRows,
                widthDp = widthDp,
                allowedDeviceIds = allowedDeviceIds,
            )
        }
    }

    companion object {
        val TAG = logTag("Module", "Connectivity", "Widget", "Glance")
    }
}

@AndroidEntryPoint
class NetworkWidgetProvider : GlanceAppWidgetReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var widgetSettings: WidgetSettings

    override val glanceAppWidget: GlanceAppWidget = NetworkGlanceWidget()

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
        val TAG = logTag("Module", "Connectivity", "Widget", "Provider")
    }
}
