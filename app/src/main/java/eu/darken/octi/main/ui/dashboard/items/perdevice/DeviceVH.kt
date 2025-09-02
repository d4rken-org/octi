package eu.darken.octi.main.ui.dashboard.items.perdevice

import android.animation.ObjectAnimator
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.DividerItemDecoration2
import eu.darken.octi.common.lists.binding
import eu.darken.octi.common.lists.differ.update
import eu.darken.octi.databinding.DashboardDeviceItemBinding
import eu.darken.octi.main.ui.dashboard.DashboardAdapter
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.StalenessUtil
import java.time.Instant


class DeviceVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<DeviceVH.Item, DashboardDeviceItemBinding>(R.layout.dashboard_device_item, parent) {

    private val moduleAdapter = PerDeviceModuleAdapter()

    override val viewBinding = lazy {
        DashboardDeviceItemBinding.bind(itemView).also {
            it.moduleDataList.apply {
                adapter = moduleAdapter
                addItemDecoration(DividerItemDecoration2(context, DividerItemDecoration.VERTICAL))
            }
        }
    }

    override val onBindData: DashboardDeviceItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val meta = item.meta.data

        deviceIcon.setImageResource(
            when {
                item.isLimited -> R.drawable.ic_baseline_stars_24
                item.isCurrentDevice -> R.drawable.ic_baseline_home_24
                else -> when (meta.deviceType) {
                    MetaInfo.DeviceType.PHONE -> R.drawable.ic_baseline_phone_android_24
                    MetaInfo.DeviceType.TABLET -> R.drawable.ic_baseline_tablet_android_24
                    MetaInfo.DeviceType.UNKNOWN -> R.drawable.ic_baseline_question_mark_24
                }
            }
        )
        deviceLabel.text = meta.deviceLabel
            ?.let { "$it (${meta.deviceName})" }
            ?: meta.deviceName
        if (BuildConfigWrap.DEBUG) deviceLabel.append(" (#${adapterPosition + 1})")

        deviceSubtitle.apply {
            val osName = getString(R.string.module_meta_android_name_x_label, meta.androidVersionName)
            text = "$osName (API ${meta.androidApiLevel})"
        }

        octiVersion.text = if (BuildConfigWrap.DEBUG) {
            "Octi #${meta.octiGitSha}"
        } else {
            "Octi v${meta.octiVersionName}"
        }

        lastSeen.text = DateUtils.getRelativeTimeSpanString(item.meta.modifiedAt.toEpochMilli())

        val isStale = StalenessUtil.isStale(item.meta.modifiedAt)

        val finalModuleItems = if (isStale && !item.isLimited) {
            val staleItem = StaleDeviceVH.Item(
                deviceId = item.meta.deviceId,
                lastSyncTime = item.meta.modifiedAt,
                onManageDevice = item.onManageStaleDevice
            )
            listOf(staleItem) + item.moduleItems
        } else {
            item.moduleItems
        }

        // Setup expand/collapse functionality
        val isCollapsed = item.isCollapsed
        val shouldShowModules = !item.isLimited && finalModuleItems.isNotEmpty() && !isCollapsed
        
        moduleDataList.isVisible = shouldShowModules
        moduleAdapter.update(finalModuleItems)

        // Update chevron state and visibility
        expandChevron.apply {
            if (item.isLimited || finalModuleItems.isEmpty()) {
                // Hide chevron for limited devices or devices with no modules
                isVisible = false
            } else {
                isVisible = true
                // Animate chevron rotation based on collapsed state
                val rotation = if (isCollapsed) 0f else 90f
                animate().rotation(rotation).setDuration(200).start()
            }
        }

        // Single click for expand/collapse, long press for drag
        root.setOnClickListener {
            when {
                finalModuleItems.isNotEmpty() && !item.isLimited -> {
                    item.onToggleCollapse(item.meta.deviceId.id)
                }
                item.isLimited -> {
                    item.onUpgrade()
                }
            }
        }
        
        // Long press to start drag
        root.setOnLongClickListener {
            item.onStartDrag?.invoke(this@DeviceVH)
            true
        }
    }

    data class Item(
        val now: Instant,
        val meta: eu.darken.octi.module.core.ModuleData<MetaInfo>,
        val moduleItems: List<PerDeviceModuleAdapter.Item>,
        val isLimited: Boolean = false,
        val isCollapsed: Boolean = false,
        val isCurrentDevice: Boolean = false,
        val onUpgrade: (() -> Unit),
        val onManageStaleDevice: (() -> Unit),
        val onToggleCollapse: ((String) -> Unit),
        val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null,
    ) : DashboardAdapter.Item {
        override val stableId: Long = meta.deviceId.hashCode().toLong()
    }


}