package eu.darken.octi.main.ui.dashboard.items.perdevice.module

import android.text.format.DateUtils
import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDeviceBatteryItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter
import eu.darken.octi.modules.ModuleData
import eu.darken.octi.modules.meta.core.MetaInfo


class DeviceBatteryVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DeviceBatteryVH.Item, DashboardDeviceBatteryItemBinding>(
        R.layout.dashboard_device_battery_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceBatteryItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceBatteryItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val meta = item.meta.data

        deviceIcon.setImageResource(
            when (meta.deviceType) {
                MetaInfo.DeviceType.PHONE -> R.drawable.ic_baseline_phone_android_24
            }
        )
        deviceLabel.text = meta.deviceName
//        octiVersion.text = "Octi v${meta.versionName}"

        lastSeen.text = DateUtils.getRelativeTimeSpanString(item.meta.modifiedAt.toEpochMilli())
    }

    data class Item(
        val meta: ModuleData<MetaInfo>,
    ) : DashboardAdapter.Item {
        override val stableId: Long = meta.deviceId.hashCode().toLong()
    }

}