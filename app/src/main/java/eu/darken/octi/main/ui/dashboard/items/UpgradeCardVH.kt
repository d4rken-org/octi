package eu.darken.octi.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.lists.binding
import eu.darken.octi.databinding.DashboardUpgradeItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class UpgradeCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<UpgradeCardVH.Item, DashboardUpgradeItemBinding>(
        R.layout.dashboard_upgrade_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardUpgradeItemBinding.bind(itemView) }

    override val onBindData: DashboardUpgradeItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        root.setOnClickListener { item.onUpgrade() }
    }

    data class Item(
        val onUpgrade: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}