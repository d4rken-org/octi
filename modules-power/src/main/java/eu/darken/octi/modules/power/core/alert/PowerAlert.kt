package eu.darken.octi.modules.power.core.alert

import eu.darken.octi.sync.core.DeviceId
import java.time.Instant

data class PowerAlert<R : PowerAlertRule>(
    val rule: R,
    val event: PowerAlertRule.Event?
) {
    val id: PowerAlertRuleId
        get() = rule.id

    val deviceId: DeviceId
        get() = rule.deviceId

    val triggeredAt: Instant?
        get() = event?.triggeredAt

    val dismissedAt: Instant?
        get() = event?.dismissedAt
}
