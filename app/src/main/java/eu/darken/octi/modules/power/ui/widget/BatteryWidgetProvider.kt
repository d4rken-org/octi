package eu.darken.octi.modules.power.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.octi.R
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
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
import java.time.Instant
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
                updateWidget(context, appWidgetManager, appWidgetId, null)
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
            updateWidget(context, appWidgetManager, appWidgetId, newOptions)
        }
    }

    private suspend fun updateWidget(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetId: Int,
        options: Bundle?
    ) {
        log(TAG) { "updateWidget(widgetId=$widgetId, options=$options)" }

        val metaState = metaRepo.state.first()
        val powerState = powerRepo.state.first()

        val layout = createLayout(context, metaState, powerState)
        widgetManager.updateAppWidget(widgetId, layout)
    }

    private fun createLayout(
        context: Context,
        metaStates: BaseModuleRepo.State<MetaInfo>,
        powerStates: BaseModuleRepo.State<PowerInfo>
    ): RemoteViews {
        log(TAG, VERBOSE) { "createLayout(context=$context, metaStates=$metaStates, powerStates=$powerStates)" }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rows = powerStates.others.mapNotNull { powerInfo ->
            val metaInfo = metaStates.all.firstOrNull { it.deviceId == powerInfo.deviceId } ?: return@mapNotNull null

            log(TAG) { "Generating info row for ${metaInfo.data.labelOrFallback}" }
            RemoteViews(context.packageName, R.layout.module_power_widget_row).apply {
                val lastSeen = DateUtils.getRelativeTimeSpanString(metaInfo.modifiedAt.toEpochMilli())
                val labelText = if (Duration.between(metaInfo.modifiedAt, Instant.now()).toHours() > 1) {
                    "${metaInfo.data.labelOrFallback} ($lastSeen)"
                } else {
                    metaInfo.data.labelOrFallback
                }
                setTextViewText(R.id.device_label, labelText)

                setImageViewResource(
                    R.id.battery_icon,
                    if (powerInfo.data.isCharging) R.drawable.widget_battery_charging_full_24
                    else R.drawable.widget_battery_full_24
                )

                setProgressBar(
                    R.id.battery_progressbar,
                    100,
                    (powerInfo.data.battery.percent * 100).toInt(),
                    false,
                )
            }
        }

        return RemoteViews(context.packageName, R.layout.module_power_widget).apply {
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            removeAllViews(R.id.widget_root)
            rows.forEach { addView(R.id.widget_root, it) }
        }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Widget", "Provider")
    }
}