package eu.darken.octi.main.ui.dashboard.items.perdevice

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDeviceStaleItemBinding
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.StalenessUtil
import java.time.Instant


class StaleDeviceVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<StaleDeviceVH.Item, DashboardDeviceStaleItemBinding>(
        R.layout.dashboard_device_stale_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceStaleItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceStaleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val stalePeriod = StalenessUtil.formatStalePeriod(context, item.lastSyncTime)
        staleWarningText.text = getString(R.string.sync_device_stale_warning_text, stalePeriod)
        manageDeviceAction.setOnClickListener { item.onManageDevice() }
    }

    data class Item(
        val deviceId: DeviceId,
        val lastSyncTime: Instant,
        val onManageDevice: () -> Unit,
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong() + deviceId.hashCode()
    }

}