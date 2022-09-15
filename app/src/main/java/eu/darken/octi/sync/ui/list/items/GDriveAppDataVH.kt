package eu.darken.octi.sync.ui.list.items

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.octi.R
import eu.darken.octi.databinding.SyncListItemGdriveBinding
import eu.darken.octi.sync.core.Sync
import eu.darken.octi.sync.core.provider.gdrive.GoogleAccount
import eu.darken.octi.sync.ui.list.SyncListAdapter


class GDriveAppDataVH(parent: ViewGroup) :
    SyncListAdapter.BaseVH<GDriveAppDataVH.Item, SyncListItemGdriveBinding>(R.layout.sync_list_item_gdrive, parent) {

    override val viewBinding = lazy { SyncListItemGdriveBinding.bind(itemView) }

    override val onBindData: SyncListItemGdriveBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        accountLabelText.text = item.account.email
        lastSyncLabelText.text = item.state.lastSyncAt?.toString() ?: getString(R.string.sync_last_never_label)
        syncProgressIndicator.isGone = !item.state.isBusy
    }

    data class Item(
        val account: GoogleAccount,
        val state: Sync.Connector.State,
    ) : SyncListAdapter.Item {
        override val stableId: Long
            get() {
                var result = this.javaClass.hashCode().toLong()
                result = 31 * result + account.hashCode()
                return result
            }
    }
}