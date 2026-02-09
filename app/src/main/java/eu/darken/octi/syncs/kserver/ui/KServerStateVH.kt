package eu.darken.octi.syncs.kserver.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import com.google.android.material.R as MaterialR
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.syncs.kserver.R as KServerR
import eu.darken.octi.databinding.SyncListItemKserverBinding
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.ui.list.SyncListAdapter
import eu.darken.octi.syncs.kserver.core.KServer


class KServerStateVH(parent: ViewGroup) :
    SyncListAdapter.BaseVH<KServerStateVH.Item, SyncListItemKserverBinding>(R.layout.sync_list_item_kserver, parent) {

    override val viewBinding = lazy { SyncListItemKserverBinding.bind(itemView) }

    override val onBindData: SyncListItemKserverBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        title.text = when {
            item.credentials.serverAdress.domain.endsWith(".darken.eu") -> {
                "${getString(KServerR.string.sync_kserver_type_label)} (${item.credentials.serverAdress.domain})"
            }

            else -> {
                "${getString(KServerR.string.sync_kserver_type_label)} (${item.credentials.serverAdress.address})"
            }
        }
        accountText.text = item.credentials.accountId.id

        lastSyncText.apply {
            text = item.ourState.lastSyncAt
                ?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli()) }
                ?: getString(R.string.sync_last_never_label)

            if (item.ourState.lastError != null) {
                setTextColor(context.getColorForAttr(MaterialR.attr.colorError))
            } else {
                setTextColor(context.getColorForAttr(android.R.attr.textColorPrimary))
            }
        }
        lastSyncError.apply {
            isGone = item.ourState.lastError == null
            text = item.ourState.lastError?.toString()
        }

        syncProgressIndicator.isGone = !item.ourState.isBusy
        pauseIcon.isGone = !item.isPaused

        quotaText.text = item.ourState.quota
            ?.let { stats ->
                val total = Formatter.formatShortFileSize(context, stats.storageTotal)
                val used = Formatter.formatShortFileSize(context, stats.storageUsed)
                val free = Formatter.formatShortFileSize(context, stats.storageFree)
                getString(R.string.sync_quota_storage_msg, free, used, total)
            }
            ?: getString(CommonR.string.general_na_label)

        devicesText.text = item.ourState.devices?.let { ourDevices ->
            var deviceString = getQuantityString(R.plurals.general_devices_count_label, ourDevices.size)

            val devicesFromConnectors = item.otherStates.mapNotNull { it.devices }.flatten().toSet()
            val uniqueDevices = ourDevices - devicesFromConnectors
            if (uniqueDevices.isNotEmpty()) {
                val uniquesString = getQuantityString(R.plurals.general_unique_devices_count_label, uniqueDevices.size)
                deviceString += " ($uniquesString)"
            }

            deviceString
        } ?: getString(CommonR.string.general_na_label)

        staleDevicesWarning.apply {
            isGone = item.staleDevicesCount == 0
            text = getQuantityString(
                SyncR.plurals.sync_stale_devices_info_message,
                item.staleDevicesCount,
                item.staleDevicesCount
            )
        }

        itemView.setOnClickListener { item.onManage() }
    }

    data class Item(
        val credentials: KServer.Credentials,
        val ourState: SyncConnectorState,
        val otherStates: Collection<SyncConnectorState>,
        val isPaused: Boolean,
        val staleDevicesCount: Int,
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