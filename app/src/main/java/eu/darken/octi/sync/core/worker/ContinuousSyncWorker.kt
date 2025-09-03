package eu.darken.octi.sync.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.octi.R
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hasApiLevel
import eu.darken.octi.main.ui.MainActivity
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.power.ui.widget.BatteryWidgetManager
import eu.darken.octi.sync.core.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltWorker
class ContinuousSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    private val batteryWidgetManager: BatteryWidgetManager,
) : CoroutineWorker(context, params) {

    @Inject lateinit var notificationManager: NotificationManager

    private var lastSyncTime: String = ""
    private var currentInterval: Int = 5

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun doWork(): Result = try {
        currentInterval = inputData.getInt(EXTRA_INTERVAL, 5)
        log(TAG, INFO) { "Starting continuous sync worker with ${currentInterval}min interval" }

        setForeground(createForegroundInfo())

        while (!isStopped) {
            performSync()

            // Wait for the specified interval
            val delayMs = currentInterval * 60 * 1000L
            delay(delayMs)
        }

        log(TAG, INFO) { "Continuous sync worker completed" }
        Result.success()
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Bugs.report(e)
            log(TAG) { "Continuous sync worker failed: $e" }
            Result.failure()
        } else {
            log(TAG) { "Continuous sync worker cancelled" }
            Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (lastSyncTime.isNotEmpty()) {
            context.getString(R.string.sync_notification_content_with_last, currentInterval, lastSyncTime)
        } else {
            context.getString(R.string.sync_notification_content, currentInterval)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.sync_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_baseline_refresh_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setLocalOnly(true)
            .build()

        return if (hasApiLevel(Build.VERSION_CODES.Q)) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (hasApiLevel(26)) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.sync_notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun performSync() {
        log(TAG) { "performSync()" }
        try {
            moduleManager.refresh()
            delay(3000)
            syncManager.sync()
            batteryWidgetManager.refreshWidgets()

            val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            lastSyncTime = now

            // Update notification with last sync time
            setForeground(createForegroundInfo())

            log(TAG, INFO) { "Sync completed at $now" }
        } catch (e: Exception) {
            log(TAG) { "Sync failed: $e" }
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Worker", "Continuous")
        private const val CHANNEL_ID = "continuous_sync_channel"
        private const val NOTIFICATION_ID = 2001
        const val EXTRA_INTERVAL = "interval_minutes"
    }
}