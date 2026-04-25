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
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hasApiLevel
import eu.darken.octi.common.navigation.FileShareDeeplink
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.permissions.PermissionState
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.time.Clock

/**
 * Posts Android notifications when a peer device appears with new shared files.
 *
 * The first emission per remote device is treated as a silent seed — only additions on
 * subsequent emissions fire notifications. This prevents a flood on app start where cached
 * remote state replays.
 *
 * Lifecycle: [start] is called once from `App.onCreate()`. The observer runs on [AppScope]
 * and drains for the lifetime of the process.
 *
 * The seen set is persisted via [FileShareSettings.seenByDevice] so notifications survive
 * process death — `current` is computed from non-expired files only and the persisted map is
 * trimmed to currently-known devices on every emission.
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
    private val permissionState: PermissionState,
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
        val systemNotificationsGrantedFlow = permissionState.state
            .map { it[Permission.POST_NOTIFICATIONS] ?: true }
            .distinctUntilChanged()

        appScope.launch(dispatcherProvider.Default) {
            // Hydrate persisted seen-set before subscribing so the first emission's diff is
            // computed against historical state, not against an empty seed.
            try {
                val persisted = fileShareSettings.seenByDevice.value()
                persisted.forEach { (idStr, blobKeys) ->
                    seenByDevice[DeviceId(idStr)] = blobKeys.toSet()
                }
                log(TAG, VERBOSE) { "start(): hydrated ${persisted.size} device entries" }
            } catch (e: Exception) {
                log(TAG, WARN) { "start(): hydrate failed, starting fresh: ${e.message}" }
            }

            combine(
                fileShareRepo.state,
                fileShareSettings.notifyOnIncoming.flow,
                moduleManager.byDevice,
                systemNotificationsGrantedFlow,
            ) { state, notifyEnabled, byDevice, systemNotificationsGranted ->
                Snapshot(state, notifyEnabled, byDevice, systemNotificationsGranted)
            }.collect { snap ->
                var dirty = false
                val now = Clock.System.now()
                val knownDevices = snap.state.others.map { it.deviceId }.toSet()

                for (moduleData in snap.state.others) {
                    val deviceId = moduleData.deviceId
                    // Filter expired files so an already-expired share doesn't notify after restart.
                    val current = moduleData.data.files
                        .filter { now <= it.expiresAt }
                        .map { it.blobKey }
                        .toSet()
                    val previous = seenByDevice[deviceId]

                    // (a) First time seen for this device: silent seed regardless of permission.
                    if (previous == null) {
                        seenByDevice[deviceId] = current
                        dirty = true
                        log(TAG, VERBOSE) { "start(): seeded ${current.size} files for ${deviceId.logLabel}" }
                        continue
                    }

                    // Cancel notifications for files that vanished from the sender (delete or
                    // expiry). Apply unconditionally — the system cancel call has no permission
                    // requirement, and we want the shade to clean itself up regardless of the
                    // user's notify-on-incoming opt-in.
                    val removed = previous - current
                    if (removed.isNotEmpty()) {
                        log(TAG, VERBOSE) { "removed ${removed.size} for ${deviceId.logLabel}" }
                        removed.forEach { blobKey ->
                            notificationManager.cancel(notificationIdFor(deviceId, blobKey))
                        }
                    }

                    // (b) User-level opt-out: advance silently (don't flood on opt-in).
                    if (!snap.notifyEnabled) {
                        if (previous != current) {
                            seenByDevice[deviceId] = current
                            dirty = true
                        }
                        continue
                    }

                    val added = current - previous
                    if (added.isEmpty()) {
                        // No additions — advance the seen set so we don't re-notify.
                        if (previous != current) {
                            seenByDevice[deviceId] = current
                            dirty = true
                        }
                        continue
                    }

                    // (c) System-level checks before notifying. If delivery would be silently
                    // dropped, do NOT advance seenByDevice — so once the user enables
                    // notifications and permissionState re-emits, `added` recomputes against
                    // the stale `previous` and we notify then.
                    val channelAllowsDelivery = if (hasApiLevel(26)) {
                        notificationManager.getNotificationChannel(CHANNEL_ID)?.importance !=
                            NotificationManager.IMPORTANCE_NONE
                    } else true

                    if (!snap.systemNotificationsGranted || !channelAllowsDelivery) {
                        log(TAG, INFO) {
                            "system notifications unavailable (perm=${snap.systemNotificationsGranted}, " +
                                "channel=$channelAllowsDelivery), deferring ${added.size} for ${deviceId.logLabel}"
                        }
                        continue
                    }

                    val deviceLabel = snap.byDevice.devices[deviceId]
                        ?.firstNotNullOfOrNull { it.data as? MetaInfo }
                        ?.labelOrFallback
                        ?: deviceId.id.take(8)

                    for (blobKey in added) {
                        val sharedFile = moduleData.data.files.find { it.blobKey == blobKey } ?: continue
                        try {
                            notify(deviceId, deviceLabel, sharedFile)
                        } catch (e: Exception) {
                            log(TAG, WARN) { "notify failed for ${sharedFile.name}: ${e.message}" }
                        }
                    }
                    seenByDevice[deviceId] = current
                    dirty = true
                }

                // Trim entries for devices no longer present in fileShareRepo. Use that flow
                // (not moduleManager.byDevice) because byDevice has transient holes during sync
                // reconnects and would otherwise drop a live device's seed. Cancel any
                // notifications still showing for the purged devices first.
                val removedDevices = seenByDevice.keys - knownDevices
                if (removedDevices.isNotEmpty()) {
                    removedDevices.forEach { deviceId ->
                        val keys = seenByDevice.remove(deviceId)
                        keys?.forEach { blobKey ->
                            notificationManager.cancel(notificationIdFor(deviceId, blobKey))
                        }
                    }
                    dirty = true
                }

                if (dirty) {
                    try {
                        fileShareSettings.seenByDevice.value(
                            seenByDevice.mapKeys { it.key.id }
                        )
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to persist seenByDevice: ${e.message}" }
                    }
                }
            }
        }
    }

    private data class Snapshot(
        val state: BaseModuleRepo.State<FileShareInfo>,
        val notifyEnabled: Boolean,
        val byDevice: ModuleManager.ByDevice,
        val systemNotificationsGranted: Boolean,
    )

    private fun notify(deviceId: DeviceId, deviceLabel: String, sharedFile: FileShareInfo.SharedFile) {
        log(TAG) { "notify(device=${deviceId.logLabel}, file=${sharedFile.name})" }

        val deeplinkIntent = FileShareDeeplink.buildIntent(context, deviceId.id)
            ?: run {
                log(TAG) { "notify(): no launch intent for package, skipping" }
                return
            }
        val openPi = PendingIntent.getActivity(
            context,
            notificationIdFor(deviceId, sharedFile.blobKey),
            deeplinkIntent,
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
