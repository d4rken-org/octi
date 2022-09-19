package eu.darken.octi.syrvs.jserver.ui

import android.view.ViewGroup
import androidx.core.view.isGone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.darken.octi.R
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.databinding.SyncListItemJserverBinding
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.ui.list.SyncListAdapter
import eu.darken.octi.syrvs.jserver.core.JServer


class JServerStateVH(parent: ViewGroup) :
    SyncListAdapter.BaseVH<JServerStateVH.Item, SyncListItemJserverBinding>(R.layout.sync_list_item_jserver, parent) {

    override val viewBinding = lazy { SyncListItemJserverBinding.bind(itemView) }

    override val onBindData: SyncListItemJserverBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        title.text = "${getString(R.string.syrv_jserver_type_label)} (${item.credentials.serverAdress.domain})"

        accountText.text = item.credentials.accountId.id

        lastSyncText.apply {
            text = item.state.lastSyncAt?.toString() ?: getString(R.string.sync_last_never_label)
            if (item.state.lastError != null) {
                setTextColor(context.getColorForAttr(R.attr.colorError))
            } else {
                setTextColor(context.getColorForAttr(android.R.attr.textColorPrimary))
            }
        }
        syncProgressIndicator.isGone = !item.state.isBusy

        wipeAction.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setMessage(R.string.syrv_jserver_wipe_confirmation_desc)
                setPositiveButton(R.string.general_wipe_action) { _, _ ->
                    item.onWipe(item.state)
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }

        disconnectAction.setOnClickListener {
            MaterialAlertDialogBuilder(context).apply {
                setMessage(R.string.syrv_jserver_disconnect_confirmation_desc)
                setPositiveButton(R.string.general_disconnect_action) { _, _ ->
                    item.onDisconnect(item.credentials)
                }
                setNegativeButton(R.string.general_cancel_action) { _, _ ->

                }
            }.show()
        }
    }

    data class Item(
        val credentials: JServer.Credentials,
        val state: SyncConnector.State,
        val onWipe: (SyncConnector.State) -> Unit,
        val onDisconnect: (JServer.Credentials) -> Unit,
    ) : SyncListAdapter.Item {
        override val stableId: Long
            get() {
                var result = this.javaClass.hashCode().toLong()
                result = 31 * result + credentials.hashCode()
                return result
            }
    }
}