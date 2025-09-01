package eu.darken.octi.sync.ui.devices

import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.octi.R
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.databinding.SyncDevicesItemDefaultBinding
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.StalenessUtil
import java.time.Instant


class DefaultSyncDeviceVH(parent: ViewGroup) :
    SyncDevicesAdapter.BaseVH<DefaultSyncDeviceVH.Item, SyncDevicesItemDefaultBinding>(
        R.layout.sync_devices_item_default,
        parent
    ) {

    override val viewBinding = lazy { SyncDevicesItemDefaultBinding.bind(itemView) }

    override val onBindData: SyncDevicesItemDefaultBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        icon.setImageResource(
            when (item.metaInfo?.deviceType) {
                MetaInfo.DeviceType.PHONE -> R.drawable.ic_baseline_phone_android_24
                MetaInfo.DeviceType.TABLET -> R.drawable.ic_baseline_tablet_android_24
                else -> R.drawable.ic_baseline_question_mark_24
            }
        )
        title.text = item.metaInfo?.labelOrFallback
        subtitle.text = item.deviceId.id
        octiVersion.text = item.metaInfo?.octiVersionName
        lastSeen.text = item.lastSeen?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli()) }
        staleWarning.apply {
            val isStale = StalenessUtil.isStale(item.lastSeen)
            text = if (isStale && item.lastSeen != null) {
                val stalePeriod = StalenessUtil.formatStalePeriod(context, item.lastSeen)
                getString(R.string.sync_device_stale_warning_text, stalePeriod)
            } else ""
            isGone = text.isEmpty()
        }
        errorDesc.apply {
            text = item.error?.asLog()
            isGone = text.isEmpty()
        }
        itemView.setOnClickListener { item.onClick() }
    }

    data class Item(
        val deviceId: DeviceId,
        val metaInfo: MetaInfo?,
        val lastSeen: Instant?,
        val error: Exception?,
        val onClick: () -> Unit,
    ) : SyncDevicesAdapter.Item {
        override val stableId: Long
            get() = deviceId.hashCode().toLong()
    }
}

