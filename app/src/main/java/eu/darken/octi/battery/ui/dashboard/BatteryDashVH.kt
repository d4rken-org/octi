package eu.darken.octi.battery.ui.dashboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardWelcomeItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class BatteryDashVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<BatteryDashVH.Item, DashboardWelcomeItemBinding>(R.layout.dashboard_welcome_item, parent) {

    override val viewBinding = lazy { DashboardWelcomeItemBinding.bind(itemView) }

    override val onBindData: DashboardWelcomeItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        itemView.setOnClickListener { item.onDismiss() }
    }

    data class Item(
        val onDismiss: () -> Unit
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}