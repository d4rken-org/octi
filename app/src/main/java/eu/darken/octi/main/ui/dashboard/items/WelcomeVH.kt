package eu.darken.octi.main.ui.dashboard.items

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.lists.binding
import eu.darken.octi.databinding.DashboardWelcomeItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter


class WelcomeVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<WelcomeVH.Item, DashboardWelcomeItemBinding>(R.layout.dashboard_welcome_item, parent) {

    override val viewBinding = lazy { DashboardWelcomeItemBinding.bind(itemView) }

    override val onBindData: DashboardWelcomeItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        itemView.setOnClickListener { item.onDismiss() }
    }

    data class Item(
        val onDismiss: () -> Unit
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}