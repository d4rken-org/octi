package eu.darken.octi.battery.core

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepo @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val powerManager: PowerManager,
) {

    private val powerEventsInternal = MutableSharedFlow<PowerEvent>()
    val powerEvents: Flow<PowerEvent> = powerEventsInternal

    private val isPowerConnected: Flow<Boolean> = callbackFlow {
        fun updateState(isConnected: Boolean) {
            appScope.launch {
                send(isConnected)
            }
        }

        var isRegistered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG) { "isPowerConnected new intent: $intent" }
                updateState(true)
            }
        }

        log(TAG) { "isCharging receiver: $receiver" }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        isRegistered = true

        awaitClose {
            log(TAG) { "isCharging flow is closing (isRegistered=$isRegistered)" }
            if (isRegistered) context.unregisterReceiver(receiver)
        }
    }
        .setupCommonEventHandlers(TAG) { "isPowerConnected" }
        .shareIn(appScope, started = SharingStarted.WhileSubscribed(), replay = 0)

    private val batteryInfo: Flow<BatteryInfo> = callbackFlow {
        fun update(info: BatteryInfo) {
            appScope.launch { send(info) }
        }

        var isRegistered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG, VERBOSE) { "batteryState new intent: $intent" }
                update(BatteryInfo.fromIntent(intent))
            }
        }
        log(TAG) { "batteryState receiver: $receiver" }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        isRegistered = true

        awaitClose {
            log(TAG) { "batteryState flow is closing (isRegistered=$isRegistered)" }
            if (isRegistered) context.unregisterReceiver(receiver)
        }
    }
        .setupCommonEventHandlers(TAG) { "batteryState" }
        .shareIn(appScope, started = SharingStarted.WhileSubscribed(), replay = 0)

    val powerStatus: Flow<PowerStatus> = batteryInfo
        .map { batteryInfo ->
            PowerStatus(
                battery = batteryInfo
            )
        }
        .setupCommonEventHandlers(TAG) { "powerStatus" }
        .shareLatest(appScope, SharingStarted.Lazily, TAG)


    @SuppressLint("WakelockTimeout")
    fun start() {
        log(TAG) { "start()" }

        isPowerConnected
            .map {
                val event = if (it) PowerEvent.PowerConnected else PowerEvent.PowerDisconnected
                log(TAG) { "Forwarding $event" }
                powerEventsInternal.emit(event)
            }
            .launchIn(appScope)
    }

    companion object {
        private val TAG = logTag("Battery", "Repo")
    }
}