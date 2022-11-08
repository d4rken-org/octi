package eu.darken.octi.modules.clipboard

import android.view.ViewGroup
import android.widget.Toast
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

        secondary.text = when (clip.type) {
            ClipboardItem.Type.EMPTY -> getString(R.string.general_empty_label)
            ClipboardItem.Type.SIMPLE_TEXT -> clip.data.utf8()
            else -> clip.data.toString()
        }.let { "\"$it\"" }

        pasteAction.setOnClickListener {
            item.onPasteClicked()
            Toast.makeText(context, R.string.module_clipboard_copied_os_to_octi, Toast.LENGTH_SHORT).show()
        }
        copyAction.setOnClickListener {
            it.requestFocus()
            item.onCopyClicked(clip)
            Toast.makeText(context, R.string.module_clipboard_copied_octi_to_os, Toast.LENGTH_SHORT).show()
        }
    }

    data class Item(
        val data: ModuleData<ClipboardItem>,
        val isOurDevice: Boolean,
        val onPasteClicked: (() -> Unit),
        val onCopyClicked: ((ClipboardItem) -> Unit),
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.moduleId.hashCode().toLong()
    }

}