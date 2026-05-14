package eu.darken.octi.modules.clipboard.ui.widget

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.widget.WidgetSettings
import eu.darken.octi.common.widget.WidgetTheme
import eu.darken.octi.common.widget.widgetDefaultColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ClipboardGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        log(TAG, VERBOSE) { "provideGlance(id=$id)" }

        val ep = EntryPointAccessors.fromApplication(context, ClipboardWidgetEntryPoint::class.java)
        val metaRepo = ep.metaRepo()
        val clipboardRepo = ep.clipboardRepo()
        val upgradeRepo = ep.upgradeRepo()
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

            // Optimistic Pro state: initial null = render content. Only when upgradeInfo emits
            // a definitive `isPro = false` do we swap to the upgrade placeholder.
            val proInfo = upgradeRepo.upgradeInfo.collectAsState(initial = null).value
            if (proInfo != null && !proInfo.isPro) {
                ClipboardWidgetUpgradeRequired(themeColors = themeColors ?: widgetDefaultColors())
                return@provideContent
            }

            val metaState = metaRepo.state.collectAsState(initial = null)
            val clipboardState = clipboardRepo.state.collectAsState(initial = null)

            val heightDp = LocalSize.current.height.value
            val maxRows = if (heightDp > 0) {
                val available = heightDp - ClipboardWidgetSizing.FIXED_OVERHEAD_DP
                maxOf(0, (available / ClipboardWidgetSizing.ROW_SLOT_DP).toInt())
            } else {
                Int.MAX_VALUE
            }

            ClipboardWidgetContent(
                metaState = metaState.value,
                clipboardState = clipboardState.value,
                themeColors = themeColors,
                maxRows = maxRows,
                allowedDeviceIds = allowedDeviceIds,
            )
        }
    }

    companion object {
        val TAG = logTag("Module", "Clipboard", "Widget", "Glance")
    }
}

@Composable
private fun ClipboardWidgetUpgradeRequired(themeColors: WidgetTheme.Colors) {
    val containerColor = ColorProvider(Color(themeColors.containerBg))
    val onContainerColor = ColorProvider(Color(themeColors.onContainer))
    val ctx = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(containerColor)
            .clickable(actionRunCallback<ClipboardUpgradeAction>())
            .padding(12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = ctx.getString(CommonR.string.widget_upgrade_required_title),
                style = TextStyle(
                    color = onContainerColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = ctx.getString(CommonR.string.widget_upgrade_required_action),
                style = TextStyle(color = onContainerColor),
            )
        }
    }
}

@AndroidEntryPoint
class ClipboardWidgetProvider : GlanceAppWidgetReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var widgetSettings: WidgetSettings

    override val glanceAppWidget: GlanceAppWidget = ClipboardGlanceWidget()

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
        val TAG = logTag("Module", "Clipboard", "Widget", "Provider")
    }
}
