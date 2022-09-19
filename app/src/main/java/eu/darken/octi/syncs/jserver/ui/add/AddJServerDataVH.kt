package eu.darken.octi.syncs.jserver.ui.add

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.SyncAddItemJserverBinding
import eu.darken.octi.sync.ui.add.SyncAddAdapter


class AddJServerDataVH(parent: ViewGroup) :
    SyncAddAdapter.BaseVH<AddJServerDataVH.Item, SyncAddItemJserverBinding>(R.layout.sync_add_item_jserver, parent) {

    override val viewBinding = lazy { SyncAddItemJserverBinding.bind(itemView) }

    override val onBindData: SyncAddItemJserverBinding.(
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