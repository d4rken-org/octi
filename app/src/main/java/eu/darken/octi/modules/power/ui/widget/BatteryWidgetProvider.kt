package eu.darken.octi.modules.power.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.main.ui.MainActivity
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Duration
import javax.inject.Inject

@AndroidEntryPoint
class BatteryWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var metaRepo: MetaRepo
    @Inject lateinit var powerRepo: PowerRepo
    @AppScope @Inject lateinit var appScope: CoroutineScope

    private var asyncBarrier: PendingResult? = null

    private fun executeAsync(
        tag: String,
        timeout: Duration = Duration.ofSeconds(10),
        block: suspend () -> Unit
    ) {
        val start = System.currentTimeMillis()
        asyncBarrier = goAsync()
        log(TAG, VERBOSE) { "executeAsync($tag) starting asyncBarrier=$asyncBarrier " }

        appScope.launch {
            try {
                withTimeout(timeout.toMillis()) { block() }
            } catch (e: Exception) {
                log(TAG, ERROR) { "executeAsync($tag) failed: ${e.asLog()}" }
            } finally {
                asyncBarrier?.finish()
                val stop = System.currentTimeMillis()
                log(TAG, VERBOSE) { "executeAsync($tag) DONE (${stop - start}ms) " }
            }
        }

        log(TAG, VERBOSE) { "executeAsync($block) leaving" }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        log(TAG) { "onUpdate(appWidgetIds=${appWidgetIds.toList()})" }
        executeAsync("onUpdate") {
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        log(TAG) { "onAppWidgetOptionsChanged(appWidgetId=$appWidgetId, newOptions=$newOptions)" }
        executeAsync("onAppWidgetOptionsChanged") {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private suspend fun updateWidget(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetId: Int,
    ) {
        log(TAG) { "updateWidget(widgetId=$widgetId)" }

        val widgetOptions = widgetManager.getAppWidgetOptions(widgetId)
        val maxHeightDp = widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        // Each row: 30dp bar + 2dp vertical padding = 32dp, container: 8dp padding top + bottom = 16dp
        val maxRows = if (maxHeightDp > 0) maxOf(1, (maxHeightDp - 16) / 32) else Int.MAX_VALUE
        log(TAG, VERBOSE) { "updateWidget: maxHeightDp=$maxHeightDp, maxRows=$maxRows" }

        val themeColors = parseThemeColors(widgetOptions)
        log(TAG, VERBOSE) { "updateWidget: themeColors=$themeColors" }

        val metaState = metaRepo.state.first()
        val powerState = powerRepo.state.first()

        val layout = createLayout(context, metaState, powerState, maxRows, themeColors)
        widgetManager.updateAppWidget(widgetId, layout)
    }

    private fun parseThemeColors(options: Bundle): WidgetTheme.Colors? = try {
        val mode = options.getString(WidgetTheme.KEY_THEME_MODE)
        when (mode) {
            WidgetTheme.MODE_CUSTOM -> {
                val bg = options.getInt(WidgetTheme.KEY_CUSTOM_BG)
                val accent = options.getInt(WidgetTheme.KEY_CUSTOM_ACCENT)
                WidgetTheme.deriveColors(bg, accent)
            }

            else -> null // Material You or unset — use default resource colors
        }
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to parse theme colors: ${e.asLog()}" }
        null
    }

    private fun createLayout(
        context: Context,
        metaStates: BaseModuleRepo.State<MetaInfo>,
        powerStates: BaseModuleRepo.State<PowerInfo>,
        maxRows: Int,
        themeColors: WidgetTheme.Colors?,
    ): RemoteViews {
        log(TAG, VERBOSE) { "createLayout(context=$context, metaStates=$metaStates, powerStates=$powerStates)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rows = powerStates.others
            .mapNotNull { powerInfo ->
                val metaInfo = metaStates.all.firstOrNull { it.deviceId == powerInfo.deviceId }
                metaInfo?.let { powerInfo to it }
            }
            .sortedBy { (_, metaInfo) -> metaInfo.data.labelOrFallback.lowercase() }
            .take(maxRows)
            .map { (powerInfo, metaInfo) ->
                log(TAG) { "Generating info row for ${metaInfo.data.labelOrFallback}" }
                val percent = (powerInfo.data.battery.percent * 100).toInt()
                val lastSeen = DateUtils.getRelativeTimeSpanString(
                    metaInfo.modifiedAt.toEpochMilli(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                )
                val deviceName = metaInfo.data.labelOrFallback
                val labelText = SpannableStringBuilder().apply {
                    append(deviceName)
                    setSpan(StyleSpan(Typeface.BOLD), 0, deviceName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append(" · $lastSeen")
                }
                RemoteViews(context.packageName, R.layout.module_power_widget_row).apply {
                    setTextViewText(R.id.device_label, labelText)

                    setImageViewResource(
                        R.id.battery_icon,
                        if (powerInfo.data.isCharging) R.drawable.widget_battery_charging_full_24
                        else R.drawable.widget_battery_full_24
                    )

                    setTextViewText(R.id.charge_percent, "$percent%")

                    setProgressBar(
                        R.id.battery_progressbar,
                        100,
                        percent,
                        false,
                    )

                    applyRowColors(themeColors)
                }
            }

        return RemoteViews(context.packageName, R.layout.module_power_widget).apply {
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            removeAllViews(R.id.widget_root)
            rows.forEach { addView(R.id.widget_root, it) }
            if (themeColors != null) {
                setInt(R.id.widget_root, "setBackgroundColor", themeColors.containerBg)
            }
        }
    }

    private fun RemoteViews.applyRowColors(colors: WidgetTheme.Colors?) {
        if (colors == null) return

        // Percent text works on all API levels
        setTextColor(R.id.charge_percent, colors.onContainer)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Progress bar tinting (API 31+)
            setColorStateList(
                R.id.battery_progressbar,
                "setProgressTintList",
                ColorStateList.valueOf(colors.barFill),
            )
            setColorStateList(
                R.id.battery_progressbar,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(colors.barTrack),
            )

            // In-bar icon and label
            setColorStateList(
                R.id.battery_icon,
                "setImageTintList",
                ColorStateList.valueOf(colors.icon),
            )
            setTextColor(R.id.device_label, colors.icon)
        }
        // On API 23-30: in-bar elements (icon, device_label) keep default colors
        // since the progress bar can't be custom-tinted
    }

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Provider")
    }
}
