package eu.darken.octi.modules.apps.ui.appslist

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.common.coil.loadAppIcon
import eu.darken.octi.common.lists.binding
import eu.darken.octi.databinding.ModuleAppsListDefaultItemBinding
import eu.darken.octi.modules.apps.core.AppsInfo


class DefaultPkgVH(parent: ViewGroup) :
    AppsListAdapter.BaseVH<DefaultPkgVH.Item, ModuleAppsListDefaultItemBinding>(
        R.layout.module_apps_list_default_item,
        parent
    ) {

    override val viewBinding = lazy { ModuleAppsListDefaultItemBinding.bind(itemView) }

    override val onBindData: ModuleAppsListDefaultItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val pkg = item.pkg

        icon.loadAppIcon(pkg)

        primary.text = pkg.label ?: pkg.packageName
        secondary.text = "${pkg.versionName} (${pkg.versionCode}) - ${pkg.packageName}"

        root.setOnClickListener { item.onClick() }
    }

    data class Item(
        val pkg: AppsInfo.Pkg,
        val onClick: () -> Unit,
    ) : AppsListAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}