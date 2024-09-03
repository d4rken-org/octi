package eu.darken.octi.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.lists.binding
import eu.darken.octi.databinding.DashboardUpdateItemBinding
import eu.darken.octi.main.core.updater.UpdateChecker
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class UpdateCardVH(parent: ViewGroup) : DashboardAdapter.BaseVH<UpdateCardVH.Item, DashboardUpdateItemBinding>(
    R.layout.dashboard_update_item,
    parent
) {

    override val viewBinding = lazy { DashboardUpdateItemBinding.bind(itemView) }

    override val onBindData: DashboardUpdateItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        body.text = getString(
            R.string.updates_dashcard_body,
            "v${BuildConfigWrap.VERSION_NAME}",
            item.update.versionName,
        )

        root.setOnClickListener { item.onViewUpdate() }
        viewAction.setOnClickListener { item.onViewUpdate() }
        dismissAction.setOnClickListener { item.onDismiss() }
        updateAction.setOnClickListener { item.onUpdate() }
    }

    data class Item(
        val update: UpdateChecker.Update,
        val onDismiss: () -> Unit,
        val onViewUpdate: () -> Unit,
        val onUpdate: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}