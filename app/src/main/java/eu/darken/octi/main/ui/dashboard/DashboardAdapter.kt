package eu.darken.octi.main.ui.dashboard

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
import eu.darken.octi.main.ui.dashboard.items.DeviceLimitVH
import eu.darken.octi.main.ui.dashboard.items.PermissionVH
import eu.darken.octi.main.ui.dashboard.items.SyncSetupVH
import eu.darken.octi.main.ui.dashboard.items.UpgradeCardVH
import eu.darken.octi.main.ui.dashboard.items.perdevice.DeviceVH
import javax.inject.Inject


class DashboardAdapter @Inject constructor() :
    ModularAdapter<DashboardAdapter.BaseVH<DashboardAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DashboardAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is UpgradeCardVH.Item }) { UpgradeCardVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is SyncSetupVH.Item }) { SyncSetupVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DeviceLimitVH.Item }) { DeviceLimitVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is PermissionVH.Item }) { PermissionVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is DeviceVH.Item }) { DeviceVH(it) })
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