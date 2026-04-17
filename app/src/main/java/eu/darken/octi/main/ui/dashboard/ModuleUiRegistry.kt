package eu.darken.octi.main.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.apps.AppsModule
import eu.darken.octi.modules.clipboard.ClipboardModule
import eu.darken.octi.modules.connectivity.ConnectivityModule
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.modules.wifi.WifiModule
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.files.R as FilesR
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.wifi.R as WifiR

data class ModuleUiSpec(
    val id: ModuleId,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val hasDashboardTile: Boolean,
)

object ModuleUiRegistry {

    val entries: List<ModuleUiSpec> = listOf(
        ModuleUiSpec(PowerModule.MODULE_ID, Icons.TwoTone.BatteryFull, PowerR.string.module_power_label, hasDashboardTile = true),
        ModuleUiSpec(WifiModule.MODULE_ID, Icons.TwoTone.Wifi, WifiR.string.module_wifi_label, hasDashboardTile = true),
        ModuleUiSpec(ConnectivityModule.MODULE_ID, Icons.TwoTone.CellTower, ConnectivityR.string.module_connectivity_label, hasDashboardTile = true),
        ModuleUiSpec(ClipboardModule.MODULE_ID, Icons.TwoTone.ContentPaste, ClipboardR.string.module_clipboard_label, hasDashboardTile = true),
        ModuleUiSpec(FileShareModule.MODULE_ID, Icons.AutoMirrored.TwoTone.InsertDriveFile, FilesR.string.module_files_label, hasDashboardTile = true),
        ModuleUiSpec(AppsModule.MODULE_ID, Icons.TwoTone.Apps, AppsR.string.module_apps_label, hasDashboardTile = true),
        ModuleUiSpec(MetaModule.MODULE_ID, Icons.TwoTone.Info, MetaR.string.module_meta_label, hasDashboardTile = false),
    )

    val orderedIds: List<ModuleId> = entries.map { it.id }
    val orderedTileIds: List<String> = entries.filter { it.hasDashboardTile }.map { it.id.id }

    fun byId(id: ModuleId): ModuleUiSpec? = entries.firstOrNull { it.id == id }
    fun byIdString(idString: String): ModuleUiSpec? = entries.firstOrNull { it.id.id == idString }

    fun iconFor(id: ModuleId): ImageVector = byId(id)?.icon ?: Icons.TwoTone.QuestionMark
    fun iconForString(idString: String): ImageVector = byIdString(idString)?.icon ?: Icons.TwoTone.QuestionMark
}
