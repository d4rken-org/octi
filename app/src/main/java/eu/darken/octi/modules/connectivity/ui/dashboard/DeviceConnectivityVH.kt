package eu.darken.octi.modules.connectivity.ui.dashboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDeviceConnectivityItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.connectivity.ui.iconRes


class DeviceConnectivityVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<DeviceConnectivityVH.Item, DashboardDeviceConnectivityItemBinding>(
        R.layout.dashboard_device_connectivity_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceConnectivityItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceConnectivityItemBinding.(
        item: Item,
        payloads: List<Any>,
    ) -> Unit = { item, _ ->
        val connectivity = item.data.data

        connectivityIcon.setImageResource(connectivity.connectionType.iconRes)

        val typeLabel = when (connectivity.connectionType) {
            ConnectivityInfo.ConnectionType.WIFI -> getString(ConnectivityR.string.module_connectivity_type_wifi_label)
            ConnectivityInfo.ConnectionType.CELLULAR -> getString(ConnectivityR.string.module_connectivity_type_cellular_label)
            ConnectivityInfo.ConnectionType.ETHERNET -> getString(ConnectivityR.string.module_connectivity_type_ethernet_label)
            ConnectivityInfo.ConnectionType.NONE, null -> getString(ConnectivityR.string.module_connectivity_type_none_label)
        }
        connectivityPrimary.text = "${getString(ConnectivityR.string.module_connectivity_detail_connection_type_label)}: $typeLabel"

        val localIp = connectivity.localAddressIpv4 ?: getString(ConnectivityR.string.module_connectivity_unknown_local_ip_label)
        val publicIp = connectivity.publicIp ?: getString(ConnectivityR.string.module_connectivity_unknown_public_ip_label)
        connectivitySecondary.text = "$localIp • $publicIp"

        itemView.setOnClickListener { item.onDetailClicked() }
    }

    data class Item(
        val data: ModuleData<ConnectivityInfo>,
        val onDetailClicked: () -> Unit,
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.moduleId.hashCode().toLong()
    }
}
