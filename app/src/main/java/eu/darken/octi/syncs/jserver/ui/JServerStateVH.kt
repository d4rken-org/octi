package eu.darken.octi.syncs.jserver.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.octi.R
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.databinding.SyncListItemJserverBinding
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.ui.list.SyncListAdapter
import eu.darken.octi.syncs.jserver.core.JServer


class JServerStateVH(parent: ViewGroup) :
    SyncListAdapter.BaseVH<JServerStateVH.Item, SyncListItemJserverBinding>(R.layout.sync_list_item_jserver, parent) {

    override val viewBinding = lazy { SyncListItemJserverBinding.bind(itemView) }

    override val onBindData: SyncListItemJserverBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        title.text = "${getString(R.string.sync_jserver_type_label)} (${item.credentials.serverAdress.domain})"

        accountText.text = item.credentials.accountId.id

        lastSyncText.apply {
            text = item.ourState.lastSyncAt
                ?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli()) }
                ?: getString(R.string.sync_last_never_label)

            if (item.ourState.lastError != null) {
                setTextColor(context.getColorForAttr(R.attr.colorError))
            } else {
                setTextColor(context.getColorForAttr(android.R.attr.textColorPrimary))
            }
        }
        syncProgressIndicator.isGone = !item.ourState.isBusy

        quotaText.text = item.ourState.quota
            ?.let { stats ->
                val total = Formatter.formatShortFileSize(context, stats.storageTotal)
                val used = Formatter.formatShortFileSize(context, stats.storageUsed)
                val free = Formatter.formatShortFileSize(context, stats.storageFree)
                getString(R.string.sync_quota_storage_msg, free, used, total)
            }
            ?: getString(R.string.general_na_label)

        devicesText.text = item.ourState.devices?.let { ourDevices ->
            var deviceString = getQuantityString(R.plurals.general_devices_count_label, ourDevices.size)

            val devicesFromConnectors = item.otherStates.mapNotNull { it.devices }.flatten().toSet()
            val uniqueDevices = ourDevices - devicesFromConnectors
            if (uniqueDevices.isNotEmpty()) {
                val uniquesString = getQuantityString(R.plurals.general_unique_devices_count_label, uniqueDevices.size)
                deviceString += " ($uniquesString)"
            }

            deviceString
        } ?: getString(R.string.general_na_label)

        itemView.setOnClickListener { item.onManage() }
    }

    data class Item(
        val credentials: JServer.Credentials,
        val ourState: SyncConnectorState,
        val otherStates: Collection<SyncConnectorState>,
        val onManage: () -> Unit,
    ) : SyncListAdapter.Item {
        override val stableId: Long
            get() {
                var result = this.javaClass.hashCode().toLong()
                result = 31 * result + credentials.hashCode()
                return result
            }
    }
}