package eu.darken.octi.modules.wifi.ui

import androidx.annotation.DrawableRes
import eu.darken.octi.R
import eu.darken.octi.modules.wifi.core.WifiInfo

@get:DrawableRes
val WifiInfo.receptIconRes: Int
    get() = when {
        currentWifi == null -> R.drawable.ic_baseline_network_wifi_off_24
        currentWifi.reception == null -> R.drawable.ic_baseline_network_wifi_off_24
        currentWifi.reception > 0.83f -> R.drawable.ic_baseline_network_wifi_5_bar_24
        currentWifi.reception > 0.66f -> R.drawable.ic_baseline_network_wifi_4_bar_24
        currentWifi.reception > 0.49f -> R.drawable.ic_baseline_network_wifi_3_bar_24
        currentWifi.reception > 0.33f -> R.drawable.ic_baseline_network_wifi_2_bar_24
        currentWifi.reception > 0.16f -> R.drawable.ic_baseline_network_wifi_1_bar_24
        currentWifi.reception >= 0.0f -> R.drawable.ic_baseline_network_wifi_0_bar_24
        else -> R.drawable.ic_baseline_network_wifi_error_24
    }