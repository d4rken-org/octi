package eu.darken.octi.main.ui.dashboard

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.octi.battery.ui.dashboard.BatteryDashVH
import eu.darken.octi.common.lists.BindableVH
import eu.darken.octi.common.lists.differ.AsyncDiffer
import eu.darken.octi.common.lists.differ.DifferItem
import eu.darken.octi.common.lists.differ.HasAsyncDiffer
import eu.darken.octi.common.lists.differ.setupDiffer
import eu.darken.octi.common.lists.modular.ModularAdapter
import eu.darken.octi.common.lists.modular.mods.DataBinderMod
import eu.darken.octi.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.octi.main.ui.dashboard.items.WelcomeVH
import eu.darken.octi.metainfo.ui.dashboard.MetaInfoVH
import javax.inject.Inject


class DashboardAdapter @Inject constructor() :
    ModularAdapter<DashboardAdapter.BaseVH<DashboardAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DashboardAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is WelcomeVH.Item }) { WelcomeVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is MetaInfoVH.Item }) { MetaInfoVH(it) })
        modules.add(TypedVHCreatorMod({ data[it] is BatteryDashVH.Item }) { BatteryDashVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : ModularAdapter.VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }

}