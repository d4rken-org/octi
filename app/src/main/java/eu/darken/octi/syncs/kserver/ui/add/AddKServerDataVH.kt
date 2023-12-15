package eu.darken.octi.syncs.kserver.ui.add

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.SyncAddItemKserverBinding
import eu.darken.octi.sync.ui.add.SyncAddAdapter


class AddKServerDataVH(parent: ViewGroup) :
    SyncAddAdapter.BaseVH<AddKServerDataVH.Item, SyncAddItemKserverBinding>(R.layout.sync_add_item_kserver, parent) {

    override val viewBinding = lazy { SyncAddItemKserverBinding.bind(itemView) }

    override val onBindData: SyncAddItemKserverBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        itemView.setOnClickListener { item.onClick() }
    }

    data class Item(
        val onClick: () -> Unit,
    ) : SyncAddAdapter.Item {
        override val stableId: Long = Item::class.java.hashCode().toLong()
    }
}