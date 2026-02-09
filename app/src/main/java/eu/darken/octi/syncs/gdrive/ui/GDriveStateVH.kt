package eu.darken.octi.syncs.gdrive.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import com.google.android.material.R as MaterialR
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.getColorForAttr
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.syncs.gdrive.R as GDriveR
import eu.darken.octi.databinding.SyncListItemGdriveBinding
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.ui.list.SyncListAdapter
import eu.darken.octi.syncs.gdrive.core.GoogleAccount


class GDriveStateVH(parent: ViewGroup) :
    SyncListAdapter.BaseVH<GDriveStateVH.Item, SyncListItemGdriveBinding>(R.layout.sync_list_item_gdrive, parent) {

    override val viewBinding = lazy { SyncListItemGdriveBinding.bind(itemView) }

    override val onBindData: SyncListItemGdriveBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        title.apply {
            text = getString(GDriveR.string.sync_gdrive_type_label)
            if (item.account.isAppDataScope) append(" (${getString(GDriveR.string.sync_gdrive_appdata_label)})")
        }

        accountText.text = item.account.email

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
        val account: GoogleAccount,
        val ourState: SyncConnectorState,
        val otherStates: Collection<SyncConnectorState>,
        val isPaused: Boolean,
        val staleDevicesCount: Int,
        val onManage: () -> Unit,
    ) : SyncListAdapter.Item {
        override val stableId: Long
            get() {
                var result = this.javaClass.hashCode().toLong()
                result = 31 * result + account.hashCode()
                return result
            }
    }
}

