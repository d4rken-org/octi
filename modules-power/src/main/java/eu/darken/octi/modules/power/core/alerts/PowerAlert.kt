package eu.darken.octi.modules.power.core.alerts

import com.squareup.moshi.JsonClass
import eu.darken.octi.sync.core.DeviceId
import java.time.Instant

sealed interface PowerAlert {
    val deviceId: DeviceId
    val triggeredAt: Instant?
    val dismissedAt: Instant?
}

@JsonClass(generateAdapter = true)
data class BatteryLowAlert(
    override val deviceId: DeviceId,
    override val triggeredAt: Instant? = null,
    override val dismissedAt: Instant? = null,
    val threshold: Float,
) : PowerAlert
