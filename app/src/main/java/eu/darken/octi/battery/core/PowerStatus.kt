package eu.darken.octi.battery.core

data class PowerStatus(
    val battery: BatteryInfo,
) {
    val isCharging: Boolean = setOf(BatteryInfo.Status.FULL, BatteryInfo.Status.CHARGING).contains(battery.status)

    val batteryPercent: Float = battery.batteryLevel / battery.batteryScale.toFloat()

}