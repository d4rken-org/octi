package eu.darken.octi.main.ui.dashboard.items.perdevice.module

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDevicePowerItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.modules.ModuleData
import eu.darken.octi.modules.power.core.PowerInfo


class DevicePowerVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<DevicePowerVH.Item, DashboardDevicePowerItemBinding>(
        R.layout.dashboard_device_power_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDevicePowerItemBinding.bind(itemView) }

    override val onBindData: DashboardDevicePowerItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val powerInfo = item.data.data

    }

    data class Item(
        val data: ModuleData<PowerInfo>,
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.deviceId.hashCode().toLong()
    }

}