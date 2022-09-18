package eu.darken.octi.servers.gdrive.ui

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.octi.R
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.databinding.SyncListItemGdriveBinding
import eu.darken.octi.servers.gdrive.core.GoogleAccount
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.ui.list.SyncListAdapter


class GDriveAppDataVH(parent: ViewGroup) :
    SyncListAdapter.BaseVH<GDriveAppDataVH.Item, SyncListItemGdriveBinding>(R.layout.sync_list_item_gdrive, parent) {

    override val viewBinding = lazy { SyncListItemGdriveBinding.bind(itemView) }

    override val onBindData: SyncListItemGdriveBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        accountLabelText.text = item.account.email
        lastSyncLabelText.apply {
            text = item.state.lastSyncAt?.toString() ?: getString(R.string.sync_last_never_label)
            if (item.state.lastError != null) {
                setTextColor(context.getColorForAttr(R.attr.colorError))
            } else {
                setTextColor(context.getColorForAttr(android.R.attr.textColorPrimary))
            }
        }
        syncProgressIndicator.isGone = !item.state.isBusy

        quotaText.text = item.state.stats
            ?.let { stats ->
                val total = Formatter.formatShortFileSize(context, stats.storageTotal)
                val used = Formatter.formatShortFileSize(context, stats.storageUsed)
                val free = Formatter.formatShortFileSize(context, stats.storageFree)
                getString(R.string.sync_quota_storage_msg, free, used, total)
            }
            ?: getString(R.string.general_na_label)

        wipeAction.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setPositiveButton(R.string.general_wipe_action) { _, _ ->
                    item.onWipe(item.state)
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }

        removeAction.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setPositiveButton(R.string.general_remove_action) { _, _ ->
                    item.onRemove(item.account)
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }
    }

    data class Item(
        val account: GoogleAccount,
        val state: SyncConnector.State,
        val onWipe: (SyncConnector.State) -> Unit,
        val onRemove: (GoogleAccount) -> Unit,
    ) : SyncListAdapter.Item {
        override val stableId: Long
            get() {
                var result = this.javaClass.hashCode().toLong()
                result = 31 * result + account.hashCode()
                return result
            }
    }
}