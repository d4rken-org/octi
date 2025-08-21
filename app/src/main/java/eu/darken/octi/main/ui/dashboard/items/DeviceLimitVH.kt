package eu.darken.octi.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.lists.binding
import eu.darken.octi.databinding.DashboardDeviceLimitItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class DeviceLimitVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DeviceLimitVH.Item, DashboardDeviceLimitItemBinding>(
        R.layout.dashboard_device_limit_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceLimitItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceLimitItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        body.text = buildString {
            append(getQuantityString(R.plurals.pro_device_limit_current_description, item.current))
            append(" ")
            append(getQuantityString(R.plurals.pro_device_limit_current_description, item.maximum))
        }
        upgradeAction.setOnClickListener { item.onUpgrade() }
    }

    data class Item(
        val current: Int,
        val maximum: Int,
        val onUpgrade: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}