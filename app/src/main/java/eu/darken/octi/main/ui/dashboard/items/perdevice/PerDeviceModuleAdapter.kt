package eu.darken.octi.main.ui.dashboard.items.perdevice

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.octi.common.lists.BindableVH
import eu.darken.octi.common.lists.differ.AsyncDiffer
import eu.darken.octi.common.lists.differ.DifferItem
import eu.darken.octi.common.lists.differ.HasAsyncDiffer
import eu.darken.octi.common.lists.differ.setupDiffer
import eu.darken.octi.common.lists.modular.ModularAdapter
import eu.darken.octi.common.lists.modular.mods.DataBinderMod
import eu.darken.octi.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.octi.modules.apps.ui.dashboard.DeviceAppsVH
import eu.darken.octi.modules.clipboard.ClipboardVH
import eu.darken.octi.modules.power.ui.dashboard.DevicePowerVH
import eu.darken.octi.modules.wifi.ui.dashboard.DeviceWifiVH
import javax.inject.Inject


class PerDeviceModuleAdapter @Inject constructor() :
    ModularAdapter<PerDeviceModuleAdapter.BaseVH<PerDeviceModuleAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<PerDeviceModuleAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is DevicePowerVH.Item }) { DevicePowerVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DeviceWifiVH.Item }) { DeviceWifiVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DeviceAppsVH.Item }) { DeviceAppsVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is ClipboardVH.Item }) { ClipboardVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }

}