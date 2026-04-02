package eu.darken.octi.modules.power.core.alert

import eu.darken.octi.common.serialization.serializer.InstantSerializer
import eu.darken.octi.sync.core.DeviceId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
sealed interface PowerAlertRule {
    val id: PowerAlertRuleId
    val deviceId: DeviceId

    @Serializable
    data class Event(
        val id: PowerAlertRuleId,
        @Serializable(with = InstantSerializer::class) val triggeredAt: Instant = Instant.now(),
        @Serializable(with = InstantSerializer::class) val dismissedAt: Instant? = null,
    )
}

typealias PowerAlertRuleId = String

@Serializable
@SerialName("BATTERY_LOW")
data class BatteryLowAlertRule(
    override val deviceId: DeviceId,
    val threshold: Float,
) : PowerAlertRule {

    override val id: PowerAlertRuleId
        get() = "${deviceId.id}-batterlow"
}

@Serializable
@SerialName("BATTERY_HIGH")
data class BatteryHighAlertRule(
    override val deviceId: DeviceId,
    val threshold: Float,
) : PowerAlertRule {

    override val id: PowerAlertRuleId
        get() = "${deviceId.id}-batterhigh"
}
