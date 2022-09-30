package eu.darken.octi.modules.power.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.BatteryManager.*
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.ifApiLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/*
    TODO
    Trigger worker and update for these events
    <action android:name="android.intent.action.BATTERY_LOW"/>
    <action android:name="android.intent.action.BATTERY_OKAY"/>
 */
@Singleton
class PowerInfoSource @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val powerManager: PowerManager,
    private val batteryManager: BatteryManager,
    private val powerSettings: PowerSettings,
) : eu.darken.octi.module.core.ModuleInfoSource<PowerInfo> {

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

    private val batteryChangedRaw: Flow<Intent> = callbackFlow {
        var isRegistered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                log(TAG, VERBOSE) { "batteryState new intent: $intent" }
                appScope.launch { send(intent) }
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

    private val battery = batteryChangedRaw
        .map { intent ->
            PowerInfo.Battery(
                level = intent.getIntExtra(EXTRA_LEVEL, -1),
                scale = intent.getIntExtra(EXTRA_SCALE, -1),
                health = intent.getIntExtra(EXTRA_HEALTH, -1).takeIf { it != -1 },
                temp = intent.getIntExtra(EXTRA_TEMPERATURE, -1).takeIf { it != -1 }?.let { it / 10f },
            )
        }

    private val powerStatus = combine(
        batteryChangedRaw,
        battery,
    ) { intent, battery ->
        val status = intent.toPowerStatus()
        if (status == PowerInfo.Status.CHARGING && battery.percent == 1.0f) {
            PowerInfo.Status.FULL
        } else {
            status
        }
    }

    private fun Intent.toPowerStatus() = getIntExtra(EXTRA_STATUS, -1).let { statusValue ->
        PowerInfo.Status.values().firstOrNull { it.value == statusValue } ?: PowerInfo.Status.UNKNOWN
    }

    private val chargeIO = combine(
        batteryChangedRaw,
        powerStatus
    ) { _, newStatus ->
        val chargedFullAt = ifApiLevel(Build.VERSION_CODES.P) { batteryManager.computeChargeTimeRemaining() }
            ?.takeIf { it != -1L }
            ?.let { Instant.now().plusMillis(it) }

        if (newStatus == PowerInfo.Status.CHARGING) {
            powerSettings.chargedFullAt.value = chargedFullAt
        } else if (newStatus == PowerInfo.Status.DISCHARGING) {
            powerSettings.chargedFullAt.value = null
        } else if (newStatus == PowerInfo.Status.FULL && powerSettings.chargedFullAt.value == null) {
            log(TAG) { "We are fully charged but have no date, let's just take NOW." }
            powerSettings.chargedFullAt.value = chargedFullAt
        }

        PowerInfo.ChargeIO(
            currentNow = batteryManager
                .getIntProperty(BATTERY_PROPERTY_CURRENT_NOW).takeIf { it != Int.MIN_VALUE },
            currenAvg = batteryManager
                .getIntProperty(BATTERY_PROPERTY_CURRENT_AVERAGE).takeIf { it != Int.MIN_VALUE },
            fullSince = powerSettings.chargedFullAt.value,
            fullAt = chargedFullAt,
            emptyAt = null,
        )
    }

    override val info: Flow<PowerInfo> = combine(
        powerStatus,
        battery,
        chargeIO
    ) { powerStatus, battery, chargeIO ->
        PowerInfo(
            status = powerStatus,
            battery = battery,
            chargeIO = chargeIO,
        )
    }
        .setupCommonEventHandlers(TAG) { "info" }
        .shareLatest(appScope, SharingStarted.Lazily, TAG)


    companion object {
        private val TAG = logTag("Module", "Power", "Source")
    }
}