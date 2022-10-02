package eu.darken.octi.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.lists.binding
import eu.darken.octi.databinding.DashboardSyncsetupItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class SyncSetupVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<SyncSetupVH.Item, DashboardSyncsetupItemBinding>(
        R.layout.dashboard_syncsetup_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardSyncsetupItemBinding.bind(itemView) }

    override val onBindData: DashboardSyncsetupItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        dismissAction.setOnClickListener { item.onDismiss() }
        setupAction.setOnClickListener { item.onSetup() }
    }

    data class Item(
        val onDismiss: () -> Unit,
        val onSetup: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}