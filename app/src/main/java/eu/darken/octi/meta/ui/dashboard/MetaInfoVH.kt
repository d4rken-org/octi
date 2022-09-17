package eu.darken.octi.meta.ui.dashboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardMetaItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter
import eu.darken.octi.meta.core.MetaRepo


class MetaInfoVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<MetaInfoVH.Item, DashboardMetaItemBinding>(R.layout.dashboard_meta_item, parent) {

    override val viewBinding = lazy { DashboardMetaItemBinding.bind(itemView) }

    override val onBindData: DashboardMetaItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
//        metaTitle.text = item.state.all.joinToString("\n") { "${it.data.deviceName} (${it.data.versionName})" }
    }

    data class Item(
        val state: MetaRepo.State,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}