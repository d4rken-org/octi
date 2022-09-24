package eu.darken.octi.main.ui.dashboard.items.perdevice

import android.text.format.DateUtils
import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.lists.binding
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.databinding.DashboardDeviceItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter
import eu.darken.octi.modules.ModuleData
import eu.darken.octi.modules.meta.core.MetaInfo
import java.time.Duration
import java.time.Instant


class DeviceVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DeviceVH.Item, DashboardDeviceItemBinding>(R.layout.dashboard_device_item, parent) {

    private val moduleAdapter = PerDeviceModuleAdapter()
    override val viewBinding = lazy {
        DashboardDeviceItemBinding.bind(itemView).also {
            it.moduleDataList.adapter = moduleAdapter
        }
    }

    override val onBindData: DashboardDeviceItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val meta = item.meta.data

        deviceIcon.setImageResource(
            when (meta.deviceType) {
                MetaInfo.DeviceType.PHONE -> R.drawable.ic_baseline_phone_android_24
            }
        )
        deviceLabel.text = meta.deviceName
        deviceSubtitle.apply {
            val uptimeExtraPolated = Duration.between(meta.deviceBootedAt, item.now)
            text = getString(R.string.device_uptime_x, DateUtils.formatElapsedTime(uptimeExtraPolated.seconds))
        }

        octiVersion.text = if (BuildConfigWrap.DEBUG) {
            "Octi #${meta.octiGitSha}"
        } else {
            "Octi v${meta.octiVersionName}"
        }

        lastSeen.text = DateUtils.getRelativeTimeSpanString(item.meta.modifiedAt.toEpochMilli())

        moduleAdapter.update(item.moduleItems)
    }

    data class Item(
        val now: Instant,
        val meta: ModuleData<MetaInfo>,
        val moduleItems: List<PerDeviceModuleAdapter.Item>,
    ) : DashboardAdapter.Item {
        override val stableId: Long = meta.deviceId.hashCode().toLong()
    }

}