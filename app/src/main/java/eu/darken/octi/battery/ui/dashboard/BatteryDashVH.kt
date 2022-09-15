package eu.darken.octi.battery.ui.dashboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardBatteryItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class BatteryDashVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<BatteryDashVH.Item, DashboardBatteryItemBinding>(R.layout.dashboard_battery_item, parent) {

    override val viewBinding = lazy { DashboardBatteryItemBinding.bind(itemView) }

    override val onBindData: DashboardBatteryItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->

    }

    data class Item(
        val onDismiss: () -> Unit
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}