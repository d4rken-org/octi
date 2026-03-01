package eu.darken.octi.modules.power.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Battery0Bar
import androidx.compose.material.icons.twotone.Battery1Bar
import androidx.compose.material.icons.twotone.Battery2Bar
import androidx.compose.material.icons.twotone.Battery3Bar
import androidx.compose.material.icons.twotone.Battery4Bar
import androidx.compose.material.icons.twotone.Battery5Bar
import androidx.compose.material.icons.twotone.Battery6Bar
import androidx.compose.material.icons.twotone.BatteryChargingFull
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.automirrored.twotone.BatteryUnknown
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.octi.modules.power.core.PowerInfo

val PowerInfo.batteryIcon: ImageVector
    get() = when {
        isCharging -> Icons.TwoTone.BatteryChargingFull
        status == PowerInfo.Status.FULL -> Icons.TwoTone.BatteryFull
        battery.percent > 0.85f -> Icons.TwoTone.Battery6Bar
        battery.percent > 0.71f -> Icons.TwoTone.Battery5Bar
        battery.percent > 0.57f -> Icons.TwoTone.Battery4Bar
        battery.percent > 0.42f -> Icons.TwoTone.Battery3Bar
        battery.percent > 0.28f -> Icons.TwoTone.Battery2Bar
        battery.percent > 0.14f -> Icons.TwoTone.Battery1Bar
        battery.percent >= 0.0f -> Icons.TwoTone.Battery0Bar
        else -> Icons.AutoMirrored.TwoTone.BatteryUnknown
    }
