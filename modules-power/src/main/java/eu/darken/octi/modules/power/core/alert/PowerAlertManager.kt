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
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.module.core.device
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaRepo
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerRepo
import eu.darken.octi.modules.power.core.PowerSettings
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
            powerRepo.state
                .throttleLatest(1.seconds)
                .distinctUntilChangedBy { it.alertRelevantStateKey() },
            powerSettings.alertRules.flow,
        ) { powerStates, alertRules ->
            processAlerts(powerStates, alertRules)
        }
            .catch { log(TAG, ERROR) { "Failed to monitor power state:\n${it.asLog()}" } }
            .launchIn(appScope)
    }

    suspend fun checkAlerts() {
        log(TAG) { "checkAlerts()" }
        val powerStates = powerRepo.state.first()
        val alertRules = powerSettings.alertRules.value()
        processAlerts(powerStates, alertRules)
    }

    private suspend fun processAlerts(powerStates: ModuleRepo.State<PowerInfo>, alertRules: Set<PowerAlertRule>) {
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

                val event = alertEvents.find { it.id == rule.id }
                log(TAG) { "Found $event for $rule" }

                // Freshness gate: a peer's battery reading is only actionable while recent.
                // Stale data (peer offline for a while) — or data timestamped in the future by a
                // skewed peer clock — must not raise or keep alerts alive. Evaluated on each alert
                // check (cold-start init + per-sync checkAlerts), not via a timer.
                val dataAge = Clock.System.now() - powerState.modifiedAt
                if (dataAge > ALERT_DATA_MAX_AGE || dataAge < -CLOCK_SKEW_TOLERANCE) {
                    if (event != null) {
                        log(TAG, INFO) { "Power data for $rule is stale (age=$dataAge), clearing alert" }
                        powerSettings.alertEvents.update { events -> events.filterNot { it.id == rule.id }.toSet() }
                        notifications.dismiss(rule)
                    } else {
                        log(TAG, VERBOSE) { "Skipping stale $rule (age=$dataAge)" }
                    }
                    return@forEach
                }

                val metaState = metaRepo.device(rule.deviceId)
                if (metaState == null) {
                    log(TAG, WARN) { "No MetaInfo available for $rule" }
                    return@forEach
                }

                val isTriggered: Boolean
                val isRecovered: Boolean
                when (rule) {
                    is BatteryLowAlertRule -> {
                        isTriggered = !powerState.data.isCharging &&
                                powerState.data.battery.percent < rule.threshold
                        isRecovered = powerState.data.isCharging ||
                                powerState.data.battery.percent > (rule.threshold + 0.05f).coerceAtMost(0.95f)
                    }

                    is BatteryHighAlertRule -> {
                        isTriggered = powerState.data.isCharging &&
                                powerState.data.battery.percent >= rule.threshold
                        isRecovered = !powerState.data.isCharging ||
                                powerState.data.battery.percent < (rule.threshold - 0.05f).coerceAtLeast(0.05f)
                    }
                }

                evaluateAlert(rule, event, isTriggered, isRecovered, powerState.data, metaState.data)
            }
        }
    }

    private suspend fun evaluateAlert(
        rule: PowerAlertRule,
        event: PowerAlertRule.Event?,
        isTriggered: Boolean,
        isRecovered: Boolean,
        power: PowerInfo,
        meta: MetaInfo,
    ) {
        when {
            event == null && isTriggered && !isRecovered -> {
                log(TAG, INFO) { "Rule has triggered" }
                val newEvent = PowerAlertRule.Event(rule.id)
                powerSettings.alertEvents.update { it + newEvent }
                showNotificationIfNeeded(rule, newEvent, power, meta)
            }

            event != null && !isTriggered && isRecovered -> {
                log(TAG, INFO) { "Rule is no longer triggered" }
                powerSettings.alertEvents.update { events -> events.filterNot { it.id == rule.id }.toSet() }
                notifications.dismiss(rule)
            }

            event == null && !isTriggered -> {
                log(TAG, VERBOSE) { "Rule is not triggered" }
            }

            event != null && event.dismissedAt == null && isTriggered && !isRecovered -> {
                log(TAG, VERBOSE) { "Rule is triggered (not dismissed)" }
                showNotificationIfNeeded(rule, event, power, meta)
            }
        }
    }

    private suspend fun showNotificationIfNeeded(
        rule: PowerAlertRule,
        event: PowerAlertRule.Event,
        power: PowerInfo,
        meta: MetaInfo,
    ) {
        // `notifiedAt` is persisted, so a delivered notification is not re-posted after a process
        // restart (the in-memory-only guard we replaced reset on every cold start). Stamped after a
        // successful show() so a failed delivery is retried on the next check rather than silently lost.
        if (event.notifiedAt != null) {
            log(TAG, VERBOSE) { "Notification already shown for ${event.id} at ${event.notifiedAt}" }
            return
        }

        notifications.show(rule, power, meta)
        powerSettings.alertEvents.update { events ->
            val target = events.firstOrNull { it.id == event.id } ?: return@update events
            (events.filterNot { it.id == event.id } + target.copy(notifiedAt = Clock.System.now())).toSet()
        }
    }

    private fun ModuleRepo.State<PowerInfo>.alertRelevantStateKey(): Set<AlertRelevantPowerState> = all
        .map {
            AlertRelevantPowerState(
                deviceId = it.deviceId,
                isCharging = it.data.isCharging,
                batteryLevel = it.data.battery.level,
                batteryScale = it.data.battery.scale,
            )
        }
        .toSet()

    private data class AlertRelevantPowerState(
        val deviceId: DeviceId,
        val isCharging: Boolean,
        val batteryLevel: Int,
        val batteryScale: Int,
    )

    suspend fun setBatteryLowAlert(deviceId: DeviceId, threshold: Float?): Unit = mutex.withLock {
        log(TAG) { "setBatteryLowAlert($deviceId,$threshold)" }

        powerSettings.alertRules.update { oldRules ->
            val otherRules = oldRules.filterNot { it.deviceId == deviceId && it is BatteryLowAlertRule }
            if (threshold == null) {
                otherRules.toSet()
            } else {
                (otherRules + BatteryLowAlertRule(deviceId = deviceId, threshold = threshold)).toSet()
            }
        }

        // Clear any lingering event + notification for this rule id, whether we created, replaced, or
        // disabled it. `id` and the notification id depend only on deviceId + rule type, so a placeholder
        // threshold is fine. Dismiss unconditionally: a replaced rule that still triggers is re-shown by
        // the next evaluation, while a lowered/disabled rule must not leave a stale notification visible.
        val ruleRef = BatteryLowAlertRule(deviceId = deviceId, threshold = threshold ?: 0f)
        log(TAG) { "setBatteryLowAlert(...): Clearing any existing event for ${ruleRef.id}" }
        powerSettings.alertEvents.update { old -> old.filterNot { it.id == ruleRef.id }.toSet() }
        notifications.dismiss(ruleRef)
    }

    suspend fun setBatteryHighAlert(deviceId: DeviceId, threshold: Float?): Unit = mutex.withLock {
        log(TAG) { "setBatteryHighAlert($deviceId,$threshold)" }

        powerSettings.alertRules.update { oldRules ->
            val otherRules = oldRules.filterNot { it.deviceId == deviceId && it is BatteryHighAlertRule }
            if (threshold == null) {
                otherRules.toSet()
            } else {
                (otherRules + BatteryHighAlertRule(deviceId = deviceId, threshold = threshold)).toSet()
            }
        }

        val ruleRef = BatteryHighAlertRule(deviceId = deviceId, threshold = threshold ?: 0f)
        log(TAG) { "setBatteryHighAlert(...): Clearing any existing event for ${ruleRef.id}" }
        powerSettings.alertEvents.update { old -> old.filterNot { it.id == ruleRef.id }.toSet() }
        notifications.dismiss(ruleRef)
    }

    suspend fun dismissAlert(alertId: PowerAlertRuleId): Unit = mutex.withLock {
        log(TAG) { "dismissAlert($alertId)" }

        val rule = powerSettings.alertRules.value().singleOrNull { it.id == alertId }
        log(TAG) { "dismissAlert(...): Found rule $rule" }
        val event = powerSettings.alertEvents.value().singleOrNull { it.id == alertId }
        log(TAG) { "dismissAlert(...): Found event $event" }

        when (rule) {
            is BatteryLowAlertRule, is BatteryHighAlertRule -> when {
                event == null -> log(TAG) { "dismissAlert(...): Alert has not triggered yet" }

                event.dismissedAt != null -> log(TAG) { "dismissAlert(...): Event already dismissed" }

                else -> {
                    log(TAG) { "dismissAlert(...): Dismissing event and removing notification" }
                    powerSettings.alertEvents.update { oldEvents ->
                        val otherEvents = oldEvents.filterNot { it.id == alertId }

                        (otherEvents + event.copy(
                            dismissedAt = Clock.System.now()
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

        // A peer's battery reading is only actionable while recent; older data no longer raises alerts.
        private val ALERT_DATA_MAX_AGE = 24.hours

        // Tolerance for peer clock skew: data timestamped this far into the future is treated as stale.
        private val CLOCK_SKEW_TOLERANCE = 5.minutes
    }
}
