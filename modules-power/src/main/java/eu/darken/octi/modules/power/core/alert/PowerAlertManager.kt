package eu.darken.octi.modules.power.core.alert

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
import eu.darken.octi.module.core.device
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.core.PowerRepo
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerAlertManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val powerRepo: PowerRepo,
    private val powerSettings: PowerSettings,
    private val notifications: PowerAlertNotifications,
    private val metaRepo: MetaRepo,
) {

    private val mutex = Mutex()

    val alerts: Flow<Collection<PowerAlert<*>>> = combine(
        powerSettings.alertRules.flow,
        powerSettings.alertEvents.flow,
    ) { rules, events ->
        rules.map { rule ->
            PowerAlert(
                rule = rule,
                event = events.find { it.id == rule.id }
            )
        }
    }.replayingShare(appScope)

    init {
        combine(
            powerRepo.state.throttleLatest(1000),
            powerSettings.alertRules.flow,
        ) { powerStates, alertRules ->
            mutex.withLock {
                log(TAG, VERBOSE) {
                    "Checking ${alertRules.size} alert rules against ${powerStates.all.size} power states"
                }

                val alertEvents = powerSettings.alertEvents.value()
                log(TAG) { "Current alert events: $alertEvents" }

                alertRules.forEach { rule ->
                    val powerState = powerStates.all.find { it.deviceId == rule.deviceId }
                    log(TAG, VERBOSE) { "Checking rule $rule against $powerState" }
                    if (powerState == null) {
                        log(TAG, WARN) { "No PowerInfo available for $rule" }
                        return@forEach
                    }

                    val metaState = metaRepo.device(rule.deviceId)
                    if (metaState == null) {
                        log(TAG, WARN) { "No MetaInfo available for $rule" }
                        return@forEach
                    }

                    val event = alertEvents.find { it.id == rule.id }
                    log(TAG) { "Found $event for $rule" }

                    when (rule) {
                        is BatteryLowAlertRule -> {
                            val isTriggered =
                                !powerState.data.isCharging && powerState.data.battery.percent < rule.threshold
                            val isRecovered =
                                powerState.data.battery.percent > (rule.threshold + 0.05f).coerceAtMost(0.95f)
                            when {
                                event == null && isTriggered && !isRecovered -> {
                                    log(TAG, INFO) { "Rule has triggered" }
                                    val newEvent = PowerAlertRule.Event(rule.id)
                                    powerSettings.alertEvents.update { it + newEvent }
                                    notifications.show(rule, powerState.data, metaState.data)
                                }

                                event != null && !isTriggered && isRecovered -> {
                                    log(TAG, INFO) { "Rule is no longer triggered" }
                                    powerSettings.alertEvents.update { it - event }
                                    notifications.dismiss(rule)
                                }

                                event == null && !isTriggered -> {
                                    log(TAG, VERBOSE) { "Rule is not triggered" }
                                }

                                event != null && event.dismissedAt == null && isTriggered && !isRecovered -> {
                                    log(TAG, VERBOSE) { "Rule is triggered (not dismissed), updating notification" }
                                    notifications.show(rule, powerState.data, metaState.data)
                                }
                            }
                        }
                    }
                }
            }
        }
            .catch { log(TAG, ERROR) { "Failed to monitor power state:\n${it.asLog()}" } }
            .launchIn(appScope)
    }

    suspend fun setBatteryLowAlert(deviceId: DeviceId, threshold: Float?): Unit = mutex.withLock {
        log(TAG) { "setBatteryLowAlert($deviceId,$threshold)" }

        val newRule = powerSettings.alertRules.update { oldRules ->
            val otherRules = oldRules.filterNot { it.deviceId == deviceId && it is BatteryLowAlertRule }

            if (threshold == null) {
                otherRules
            } else {
                otherRules + BatteryLowAlertRule(deviceId = deviceId, threshold = threshold)
            }.toSet()
        }.new.filterIsInstance<BatteryLowAlertRule>().single()

        powerSettings.alertEvents.update { old ->
            val previousEvent = old.singleOrNull { it.id == newRule.id } ?: return@update old
            log(TAG) { "setBatteryLowAlert(...): Removing old event" }
            (old - previousEvent).toSet()
        }
    }

    suspend fun dismissAlert(alertId: PowerAlertRuleId): Unit = mutex.withLock {
        log(TAG) { "dismissAlert($alertId)" }

        val rule = powerSettings.alertRules.value().singleOrNull { it.id == alertId }
        log(TAG) { "dismissAlert(...): Found rule $rule" }
        val event = powerSettings.alertEvents.value().singleOrNull { it.id == alertId }
        log(TAG) { "dismissAlert(...): Found event $event" }

        when (rule) {
            is BatteryLowAlertRule -> when {
                event == null -> log(TAG) { "dismissAlert(...): Alert has not triggered yet" }

                event.dismissedAt != null -> log(TAG) { "dismissAlert(...): Event already dismissed" }

                else -> {
                    log(TAG) { "dismissAlert(...): Dismissing event and removing notification" }
                    powerSettings.alertEvents.update { oldEvents ->
                        val otherEvents = oldEvents.filterNot { it.id == alertId }

                        (otherEvents + event.copy(
                            dismissedAt = Instant.now()
                        )).toSet()
                    }
                    notifications.dismiss(rule)
                }
            }

            null -> log(TAG, ERROR) { "dismissAlert(...): No alert found for $alertId" }
        }
    }

    suspend fun dismissAlerts(deviceId: DeviceId): Unit {
        log(TAG) { "dismissAlerts($deviceId)" }
        alerts.first()
            .filter { it.deviceId == deviceId }
            .filter { it.event?.triggeredAt != null }
            .also { log(TAG) { "dismissAlerts(...): Dismissing ${it.size} alerts" } }
            .forEach { dismissAlert(it.id) }
    }

    companion object {
        val TAG = logTag("Module", "Power", "Alert", "Manager")
    }
}