package eu.darken.octi.modules.wifi.ui.dashboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDeviceWifiItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.modules.ModuleData
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.receptIconRes


class DeviceWifiVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<DeviceWifiVH.Item, DashboardDeviceWifiItemBinding>(
        R.layout.dashboard_device_wifi_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceWifiItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceWifiItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val wifi = item.data.data

        wifiIcon.setImageResource(wifi.receptIconRes)

        wifiPrimary.apply {
            val freqText = when (wifi.currentWifi?.freqType) {
                WifiInfo.Wifi.Type.FIVE_GHZ -> "5 Ghz"
                WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> "2.4 Ghz"
                else -> getString(R.string.general_na_label)
            }
            val sig = wifi.currentWifi?.reception ?: 0f
            val reception = when {
                sig > 0.65f -> getString(R.string.module_wifi_reception_good_label)
                sig > 0.3f -> getString(R.string.module_wifi_reception_okay_label)
                sig > 0.0f -> getString(R.string.module_wifi_reception_bad_label)
                else -> getString(R.string.general_na_label)
            }

            text = "$freqText • $reception"
        }

        wifiSecondary.apply {
            val ipText = wifi.currentWifi?.addressIpv4 ?: getString(R.string.module_wifi_unknown_ip_label)
            val ssidText = wifi.currentWifi?.ssid ?: getString(R.string.module_wifi_unknown_ssid_label)
            text = "$ssidText - $ipText"
        }
    }

    data class Item(
        val data: ModuleData<WifiInfo>,
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.deviceId.hashCode().toLong()
    }

}