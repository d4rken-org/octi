package eu.darken.octi.modules.power.core.alert

import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import eu.darken.octi.sync.core.DeviceId
import java.time.Instant

sealed interface PowerAlertRule {
    val id: PowerAlertRuleId
    val deviceId: DeviceId

    @JsonClass(generateAdapter = true)
    data class Event(
        val id: PowerAlertRuleId,
        val triggeredAt: Instant = Instant.now(),
        val dismissedAt: Instant? = null,
    )

    companion object {
        val moshiFactory: PolymorphicJsonAdapterFactory<PowerAlertRule>
            get() = PolymorphicJsonAdapterFactory.of(PowerAlertRule::class.java, "type")
                .withSubtype(BatteryLowAlertRule::class.java, "BATTERY_LOW")
    }
}

typealias PowerAlertRuleId = String

@JsonClass(generateAdapter = true)
data class BatteryLowAlertRule(
    override val deviceId: DeviceId,
    val threshold: Float,
) : PowerAlertRule {

    override val id: PowerAlertRuleId
        get() = "${deviceId.id}-batterlow"
}
