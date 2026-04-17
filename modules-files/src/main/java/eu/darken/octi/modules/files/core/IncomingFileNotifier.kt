package eu.darken.octi.modules.files.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hasApiLevel
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Posts Android notifications when a peer device appears with new shared files.
 *
 * The first emission per remote device is treated as a silent seed — only additions on
 * subsequent emissions fire notifications. This prevents a flood on app start where cached
 * remote state replays.
 *
 * Lifecycle: [start] is called once from `App.onCreate()`. The observer runs on [AppScope]
 * and drains for the lifetime of the process.
 */
@Singleton
class IncomingFileNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val fileShareRepo: FileShareRepo,
    private val fileShareSettings: FileShareSettings,
    private val moduleManager: ModuleManager,
    private val notificationManager: NotificationManager,
) {

    private val seenByDevice = ConcurrentHashMap<DeviceId, Set<String>>()

    init {
        if (hasApiLevel(26)) {
            @Suppress("NewApi")
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.module_files_notification_channel_label),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).run { notificationManager.createNotificationChannel(this) }
        }
    }

    fun start() {
        log(TAG, INFO) { "start(): observing incoming file shares" }
        combine(
            fileShareRepo.state,
            fileShareSettings.notifyOnIncoming.flow,
            moduleManager.byDevice,
        ) { state, notifyEnabled, byDevice -> Triple(state, notifyEnabled, byDevice) }
            .onEach { (state, notifyEnabled, byDevice) ->
                for (moduleData in state.others) {
                    val deviceId = moduleData.deviceId
                    val current = moduleData.data.files.map { it.blobKey }.toSet()
                    val previous = seenByDevice.put(deviceId, current)

                    if (previous == null) {
                        log(TAG, VERBOSE) { "start(): seeded ${current.size} files for ${deviceId.logLabel}" }
                        continue
                    }
                    if (!notifyEnabled) continue

                    val added = current - previous
                    if (added.isEmpty()) continue

                    val deviceLabel = byDevice.devices[deviceId]
                        ?.firstNotNullOfOrNull { it.data as? MetaInfo }
                        ?.labelOrFallback
                        ?: deviceId.id.take(8)

                    for (blobKey in added) {
                        val sharedFile = moduleData.data.files.find { it.blobKey == blobKey } ?: continue
                        notify(deviceId, deviceLabel, sharedFile)
                    }
                }
            }
            .launchIn(appScope + dispatcherProvider.Default)
    }

    private fun notify(deviceId: DeviceId, deviceLabel: String, sharedFile: FileShareInfo.SharedFile) {
        log(TAG) { "notify(device=${deviceId.logLabel}, file=${sharedFile.name})" }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: run {
                log(TAG) { "notify(): no launch intent for package, skipping" }
                return
            }
        val openPi = PendingIntent.getActivity(
            context,
            sharedFile.blobKey.hashCode(),
            launchIntent.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setChannelId(CHANNEL_ID)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_file_share_notification)
            .setOngoing(false)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(context.getString(R.string.module_files_notification_incoming_title, deviceLabel))
            .setContentText(sharedFile.name)
            .setContentIntent(openPi)

        notificationManager.notify(notificationIdFor(deviceId, sharedFile.blobKey), builder.build())
    }

    private fun notificationIdFor(deviceId: DeviceId, blobKey: String): Int {
        val hash = (deviceId.id + ":" + blobKey).hashCode()
        return NOTIFICATION_ID_BASE + (hash.absoluteValue % 10_000)
    }

    companion object {
        private val TAG = logTag("Module", "Files", "IncomingNotifier")
        private const val NOTIFICATION_ID_BASE = 2000
        // Hardcoded because Android scopes notification channels per-app; no need to prefix with
        // applicationId. Avoids pulling BuildConfigWrap into the companion initializer so this
        // class can be unit-tested without a Robolectric runtime.
        private const val CHANNEL_ID = "module.files.incoming"
    }
}
