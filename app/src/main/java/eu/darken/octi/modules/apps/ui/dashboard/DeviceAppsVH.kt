package eu.darken.octi.modules.apps.ui.dashboard

import android.view.ViewGroup
import eu.darken.octi.R
import eu.darken.octi.databinding.DashboardDeviceAppsItemBinding
import eu.darken.octi.main.ui.dashboard.items.perdevice.PerDeviceModuleAdapter
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.installerIconRes


class DeviceAppsVH(parent: ViewGroup) :
    PerDeviceModuleAdapter.BaseVH<DeviceAppsVH.Item, DashboardDeviceAppsItemBinding>(
        R.layout.dashboard_device_apps_item,
        parent
    ) {

    override val viewBinding = lazy { DashboardDeviceAppsItemBinding.bind(itemView) }

    override val onBindData: DashboardDeviceAppsItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        val apps = item.data.data

        appsPrimary.apply {
            text = resources.getQuantityString(
                R.plurals.module_apps_x_installed,
                apps.installedPackages.size,
                apps.installedPackages.size
            )
        }
        val last = apps.installedPackages.maxByOrNull { it.installedAt }
        appsSecondary.text =
            last?.let { getString(R.string.module_apps_last_installed_x, "${it.label} (${it.versionName})") }

        itemView.setOnClickListener { item.onAppsInfoClicked() }

        installAction.apply {
            setIconResource(last.installerIconRes)
            setOnClickListener { item.onInstallClicked() }
        }
    }

    data class Item(
        val data: ModuleData<AppsInfo>,
        val onAppsInfoClicked: (() -> Unit),
        val onInstallClicked: (() -> Unit),
    ) : PerDeviceModuleAdapter.Item {
        override val stableId: Long = data.moduleId.hashCode().toLong()
    }

}