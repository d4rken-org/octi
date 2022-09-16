package eu.darken.octi.main.ui.dashboard.items.permodule

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardMetaItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter
import eu.darken.octi.metainfo.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId


class MetaVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<MetaVH.Item, DashboardMetaItemBinding>(R.layout.dashboard_meta_item, parent) {

    override val viewBinding = lazy { DashboardMetaItemBinding.bind(itemView) }

    override val onBindData: DashboardMetaItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->

    }

    data class Item(
        val deviceId: DeviceId,
        val metaInfo: MetaInfo,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}