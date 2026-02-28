package eu.darken.octi.modules.connectivity.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.Lan
import androidx.compose.material.icons.twotone.LinkOff
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo

val ConnectivityInfo.ConnectionType?.icon: ImageVector
    get() = when (this) {
        ConnectivityInfo.ConnectionType.WIFI -> Icons.TwoTone.Wifi
        ConnectivityInfo.ConnectionType.CELLULAR -> Icons.TwoTone.CellTower
        ConnectivityInfo.ConnectionType.ETHERNET -> Icons.TwoTone.Lan
        ConnectivityInfo.ConnectionType.NONE, null -> Icons.TwoTone.LinkOff
    }
