package eu.darken.octi.modules.clipboard.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.LocalSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
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
import eu.darken.octi.common.upgrade.ProState
import eu.darken.octi.common.upgrade.UpgradeLauncher
import eu.darken.octi.common.upgrade.proState
import eu.darken.octi.common.widget.WidgetSettings
import eu.darken.octi.common.widget.WidgetTheme
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
        val upgradeLauncher = ep.upgradeLauncher()
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
        val configFlow = widgetSettings.config(appWidgetId)
        val proStateFlow = upgradeRepo.proState()

        provideContent {
            val instanceConfig = configFlow.collectAsState(initial = initialConfig).value
            val themeColors = instanceConfig.themeColors
            val allowedDeviceIds = instanceConfig.allowedDeviceIds.takeIf { it.isNotEmpty() }

            // Only hard-lock on a definitive Locked emission. ProState.Error is treated as
            // transient (cold-start billing failure, network blip) and falls through to
            // content rendering — same pattern as the config activity's retry behaviour.
            val proState = proStateFlow.collectAsState(initial = ProState.Checking).value
            if (proState is ProState.Locked) {
                ClipboardWidgetUpgradeRequired(
                    themeColors = themeColors,
                    upgradeLauncher = upgradeLauncher,
                )
                return@provideContent
            }

            val metaState = metaRepo.state.collectAsState(initial = null)
            val clipboardState = clipboardRepo.state.collectAsState(initial = null)

            val heightDp = LocalSize.current.height.value
            val maxRows = ClipboardWidgetSizing.maxRemoteRowsForHeight(heightDp)

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
private fun ClipboardWidgetUpgradeRequired(
    themeColors: WidgetTheme.Colors?,
    upgradeLauncher: UpgradeLauncher,
) {
    val containerColor = themeColors?.containerBg
        ?.let { ColorProvider(Color(it)) }
        ?: ColorProvider(CommonR.color.widgetContainerBackground)
    val onContainerColor = themeColors?.onContainer
        ?.let { ColorProvider(Color(it)) }
        ?: ColorProvider(CommonR.color.widgetOnContainer)
    val ctx = LocalContext.current
    val upgradeAction = actionStartActivity(upgradeLauncher.createIntent(ctx))
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetCornerRadius(16.dp)
            .background(containerColor)
            .clickable(upgradeAction)
            .padding(6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = ctx.getString(CommonR.string.widget_upgrade_required_title),
                style = TextStyle(
                    color = onContainerColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = ctx.getString(CommonR.string.widget_upgrade_required_action),
                style = TextStyle(
                    color = onContainerColor,
                    fontSize = 10.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun GlanceModifier.widgetCornerRadius(radius: Dp): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(radius) else this

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
