package eu.darken.octi.modules.power.core.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hasApiLevel
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.R
import eu.darken.octi.modules.power.core.PowerInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue


@Singleton
class PowerAlertNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        if (hasApiLevel(26)) {
            @Suppress("NewApi")
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.module_power_alerts_notification_channel_label),
                NotificationManager.IMPORTANCE_DEFAULT
            ).run { notificationManager.createNotificationChannel(this) }
        }
    }

    private fun getBuilder(alert: PowerAlertRule) = NotificationCompat.Builder(context, CHANNEL_ID).apply {
        setChannelId(CHANNEL_ID)
        priority = NotificationCompat.PRIORITY_DEFAULT
        setSmallIcon(R.drawable.ic_baseline_battery_alert_24)

        setOngoing(false)
        setAutoCancel(true)
        setLocalOnly(true)
        setOnlyAlertOnce(true)

        val openPi: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)!!.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ARG_ALERT_ID, alert.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setContentIntent(openPi)

        val deletePi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PowerAlertNotificationReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(ARG_ALERT_ID, alert.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setDeleteIntent(deletePi)
    }

    private val PowerAlertRule.notificationId: Int
        get() {
            val hash = (deviceId.toString() + this::class.java.simpleName).hashCode()
            return NOTIFICATION_ID_RANGE_STATE + (hash.absoluteValue % 101)
        }

    suspend fun show(rule: PowerAlertRule, power: PowerInfo, meta: MetaInfo) {
        log(TAG) { "show($rule, $meta)" }

        val label = meta.labelOrFallback

        val builder = getBuilder(rule).apply {
            when (rule) {
                is BatteryLowAlertRule -> {
                    setContentTitle(
                        context.getString(
                            R.string.module_power_alerts_notification_battery_low_title,
                            label
                        )
                    )
                    setContentText(
                        context.getString(
                            R.string.module_power_alerts_notification_battery_low_body,
                            label,
                            (rule.threshold * 100).toInt(),
                            (power.battery.percent * 100).toInt(),
                        )
                    )
                }
            }
        }

        notificationManager.notify(rule.notificationId, builder.build())
    }

    suspend fun dismiss(alert: PowerAlertRule) {
        log(TAG) { "dismiss($alert)" }
        notificationManager.cancel(alert.notificationId)
    }

    companion object {
        const val ARG_ALERT_ID = "alertId"
        internal const val NOTIFICATION_ID_RANGE_STATE = 1000
        val ACTION_DISMISS = "${BuildConfigWrap.APPLICATION_ID}.module.power.alert.NOTIFICATION_DISMISSED"
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.module.power.alerts"
        val TAG = logTag("Module", "Power", "Alert", "Notifications")
    }
}