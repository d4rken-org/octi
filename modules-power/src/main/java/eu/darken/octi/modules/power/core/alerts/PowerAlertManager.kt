package eu.darken.octi.modules.power.core.alerts

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.throttleLatest
import eu.darken.octi.modules.power.core.PowerRepo
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerAlertManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val powerRepo: PowerRepo,
    private val powerSettings: PowerSettings,
) {

    private val mutex = Mutex()
    val alerts: Flow<Set<PowerAlert>> = powerSettings.lowBatteryAlerts.flow.replayingShare(appScope)

    init {
        combine(
            powerRepo.state.throttleLatest(500),
            powerSettings.lowBatteryAlerts.flow,
        ) { powerState, lowBatteryAlerts ->
            mutex.withLock {
                log(TAG, VERBOSE) {
                    "Checking ${lowBatteryAlerts.size} low battery alerts against ${powerState.all.size} states"
                }
                val now = Instant.now()
                val updated = lowBatteryAlerts.mapNotNull { alert ->
                    val state = powerState.all.find { it.deviceId == alert.deviceId }
                    log(TAG, VERBOSE) { "Checking alert $alert against $state" }
                    if (state == null) return@mapNotNull alert

                    val isPowerDataStale = Duration.between(state.modifiedAt, now) > Duration.ofDays(31)
                    val isAlertStale = Duration.between(alert.triggeredAt ?: now, now) > Duration.ofDays(31)
                    if (isPowerDataStale && isAlertStale) {
                        log(TAG, WARN) { "Deleting stale alert $alert, last state was $state" }
                        return@mapNotNull null
                    }

                    if (alert.triggeredAt != null) {
                        // Alert was already triggered, has it recovered?
                        val recovery = (alert.threshold + 0.05f).coerceAtMost(0.8f)
                        if (state.data.isCharging || state.data.battery.percent > recovery) {
                            log(TAG, INFO) { "Low battery alert has recovered: $alert" }
                            alert.copy(triggeredAt = null, dismissedAt = null)
                        } else if (alert.dismissedAt != null) {
                            log(TAG) { "Low battery alert is triggered but dismissed: $alert" }
                            alert
                        } else {
                            log(TAG) { "Low battery alert is triggered: $alert" }
                            alert
                        }
                    } else {
                        // Alert was not triggered yet, does it need to be?
                        if (state.data.battery.percent < alert.threshold) {
                            alert.copy(triggeredAt = Instant.now(), dismissedAt = null).also {
                                log(TAG, INFO) { "Low battery alert has triggered: $alert" }
                            }
                        } else {
                            log(TAG, VERBOSE) { "Low battery alert is not triggered: $alert" }
                            alert
                        }
                    }
                }.toSet()
                powerSettings.lowBatteryAlerts.value(updated)
            }
        }
            .catch { log(TAG, ERROR) { "Failed to monitor power state:\n${it.asLog()}" } }
            .launchIn(appScope)
    }


    suspend fun setBatteryLowAlert(deviceId: DeviceId, threshold: Float?) = mutex.withLock {
        log(TAG) { "setBatteryLowAlert($deviceId,$threshold)" }
        powerSettings.lowBatteryAlerts.update { oldSet ->
            val existing = oldSet.find { it.deviceId == deviceId }

            if (threshold != null) {
                if (existing != null) {
                    oldSet - existing + existing.copy(
                        threshold = threshold,
                        triggeredAt = null,
                        dismissedAt = null,
                    )
                } else {
                    oldSet + BatteryLowAlert(
                        deviceId = deviceId,
                        threshold = threshold,
                    )
                }
            } else {
                oldSet.filter { it.deviceId != deviceId }
            }.toSet()
        }
    }

    suspend fun dismissBatteryLowAlert(deviceId: DeviceId) = mutex.withLock {
        log(TAG) { "dismissBatteryLowAlert($deviceId)" }

        val alert = powerSettings.lowBatteryAlerts.value().find { it.deviceId == deviceId }
        if (alert == null) {
            log(TAG) { "$deviceId has no alerts" }
            return@withLock
        }

        if (alert.triggeredAt == null) {
            log(TAG) { "$alert has not triggered yet" }
            return@withLock
        }

        if (alert.dismissedAt != null) {
            log(TAG) { "$alert is already dismissed" }
            return@withLock
        }

        powerSettings.lowBatteryAlerts.update { oldAlerts ->
            oldAlerts - alert + alert.copy(dismissedAt = Instant.now()).also {
                log(TAG, INFO) { "Alert dismissed: $it" }
            }
        }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Alert", "Manager")
    }
}