package eu.darken.octi.modules.power.core.alert

import eu.darken.octi.modules.power.core.alert.PowerAlertNotifications.Companion.NOTIFICATION_ID_RANGE_STATE
import eu.darken.octi.sync.core.DeviceId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.math.absoluteValue

class PowerAlertNotificationIdTest : BaseTest() {

    private val deviceId = DeviceId("00000000-0000-0000-0000-000000000022")

    private fun expectedId(typeKey: String): Int {
        val hash = (deviceId.toString() + typeKey).hashCode()
        return NOTIFICATION_ID_RANGE_STATE + (hash.absoluteValue % 101)
    }

    @Test
    fun `battery low notification id stays pinned to its type key`() {
        BatteryLowAlertRule(deviceId = deviceId, threshold = 0.2f).notificationId() shouldBe
            expectedId("BatteryLowAlertRule")
    }

    @Test
    fun `battery high notification id stays pinned to its type key`() {
        BatteryHighAlertRule(deviceId = deviceId, threshold = 0.8f).notificationId() shouldBe
            expectedId("BatteryHighAlertRule")
    }
}
