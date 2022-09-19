package eu.darken.octi.syncs.gdrive.ui.add

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.SyncAddItemGdriveBinding
import eu.darken.octi.sync.ui.add.SyncAddAdapter


class AddGDriveVH(parent: ViewGroup) :
    SyncAddAdapter.BaseVH<AddGDriveVH.Item, SyncAddItemGdriveBinding>(R.layout.sync_add_item_gdrive, parent) {

    override val viewBinding = lazy { SyncAddItemGdriveBinding.bind(itemView) }

    override val onBindData: SyncAddItemGdriveBinding.(
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