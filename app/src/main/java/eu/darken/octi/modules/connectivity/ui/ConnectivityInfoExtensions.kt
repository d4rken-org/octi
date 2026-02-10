package eu.darken.octi.modules.connectivity.ui

import androidx.annotation.DrawableRes
import eu.darken.octi.R
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo

@get:DrawableRes
val ConnectivityInfo.ConnectionType?.iconRes: Int
    get() = when (this) {
        ConnectivityInfo.ConnectionType.WIFI -> R.drawable.ic_wifi_24
        ConnectivityInfo.ConnectionType.CELLULAR -> R.drawable.ic_baseline_cell_tower_24
        ConnectivityInfo.ConnectionType.ETHERNET -> R.drawable.ic_baseline_lan_24
        ConnectivityInfo.ConnectionType.NONE, null -> R.drawable.ic_baseline_link_off_24
    }
