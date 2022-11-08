package eu.darken.octi.modules.clipboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDeviceClipboardItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.module.core.ModuleData


class ClipboardVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<ClipboardVH.Item, DashboardDeviceClipboardItemBinding>(
        R.layout.dashboard_device_clipboard_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceClipboardItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceClipboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val clip = item.data.data

    }

    data class Item(
        val data: ModuleData<ClipboardInfo>,
        val onPasteClicked: (() -> Unit),
        val onCopyClicked: (() -> Unit),
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.moduleId.hashCode().toLong()
    }

}