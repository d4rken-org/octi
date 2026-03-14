package eu.darken.octi.modules.wifi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.NetworkWifi1Bar
import androidx.compose.material.icons.twotone.NetworkWifi2Bar
import androidx.compose.material.icons.twotone.NetworkWifi3Bar
import androidx.compose.material.icons.twotone.SignalWifi4Bar
import androidx.compose.material.icons.twotone.SignalWifi0Bar
import androidx.compose.material.icons.twotone.SignalWifiBad
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material.icons.twotone.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.octi.modules.wifi.core.WifiInfo

val WifiInfo.receptIcon: ImageVector
    get() = when {
        currentWifi == null -> Icons.TwoTone.WifiOff
        currentWifi!!.reception == null -> Icons.TwoTone.WifiOff
        currentWifi!!.reception!! > 0.83f -> Icons.TwoTone.Wifi
        currentWifi!!.reception!! > 0.66f -> Icons.TwoTone.SignalWifi4Bar
        currentWifi!!.reception!! > 0.49f -> Icons.TwoTone.NetworkWifi3Bar
        currentWifi!!.reception!! > 0.33f -> Icons.TwoTone.NetworkWifi2Bar
        currentWifi!!.reception!! > 0.16f -> Icons.TwoTone.NetworkWifi1Bar
        currentWifi!!.reception!! >= 0.0f -> Icons.TwoTone.SignalWifi0Bar
        else -> Icons.TwoTone.SignalWifiBad
    }
