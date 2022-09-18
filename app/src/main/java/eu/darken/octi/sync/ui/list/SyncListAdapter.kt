package eu.darken.octi.sync.ui.list

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
import eu.darken.octi.servers.gdrive.ui.GDriveAppDataVH
import javax.inject.Inject


class SyncListAdapter @Inject constructor() :
    ModularAdapter<SyncListAdapter.BaseVH<SyncListAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<SyncListAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        modules.add(DataBinderMod(data))
        modules.add(TypedVHCreatorMod({ data[it] is GDriveAppDataVH.Item }) { GDriveAppDataVH(it) })
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