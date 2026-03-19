package eu.darken.octi.main.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.ui.dashboard.AppsModuleTile
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.ui.dashboard.ClipboardDashState
import eu.darken.octi.modules.clipboard.ui.dashboard.ClipboardModuleTile
import eu.darken.octi.modules.connectivity.ui.dashboard.ConnectivityModuleTile
import eu.darken.octi.modules.power.ui.dashboard.PowerDashState
import eu.darken.octi.modules.power.ui.dashboard.PowerModuleTile
import eu.darken.octi.modules.wifi.ui.dashboard.WifiDashState
import eu.darken.octi.modules.wifi.ui.dashboard.WifiModuleTile
import eu.darken.octi.sync.core.DeviceId

@Composable
fun ModuleTileGrid(
    rows: List<TileRow>,
    moduleItems: List<DashboardVM.ModuleItem>,
    deviceId: DeviceId,
    modifier: Modifier = Modifier,
    onPowerClicked: (DashboardVM.ModuleItem.Power) -> Unit,
    onPowerAlerts: (DeviceId) -> Unit,
    onWifiClicked: (DashboardVM.ModuleItem.Wifi) -> Unit,
    onWifiPermissionGrant: (Permission) -> Unit,
    onConnectivityClicked: (DashboardVM.ModuleItem.Connectivity) -> Unit,
    onAppsClicked: (DeviceId) -> Unit,
    onInstallLatestApp: (AppsInfo) -> Unit,
    onClipboardClicked: (DashboardVM.ModuleItem.Clipboard) -> Unit,
    onClearClipboard: () -> Unit,
    onShareClipboard: () -> Unit,
    onCopyClipboard: (ClipboardInfo) -> Unit,
    showMessage: (String) -> Unit,
) {
    val moduleMap = buildModuleIdMap(moduleItems)

    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            val isWideRow = row.modules.size == 1
            val isHeroRow = rowIndex == 0 && isWideRow
            Row(
                modifier = if (isWideRow) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.modules.forEach { moduleId ->
                    val tileModifier = if (isWideRow) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.weight(1f).fillMaxHeight()
                    }
                    ModuleTileDispatch(
                        moduleId = moduleId,
                        moduleMap = moduleMap,
                        modifier = tileModifier,
                        isWide = isHeroRow,
                        deviceId = deviceId,
                        onPowerClicked = onPowerClicked,
                        onPowerAlerts = onPowerAlerts,
                        onWifiClicked = onWifiClicked,
                        onWifiPermissionGrant = onWifiPermissionGrant,
                        onConnectivityClicked = onConnectivityClicked,
                        onAppsClicked = onAppsClicked,
                        onInstallLatestApp = onInstallLatestApp,
                        onClipboardClicked = onClipboardClicked,
                        onClearClipboard = onClearClipboard,
                        onShareClipboard = onShareClipboard,
                        onCopyClipboard = onCopyClipboard,
                        showMessage = showMessage,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleTileDispatch(
    moduleId: String,
    moduleMap: Map<String, DashboardVM.ModuleItem>,
    modifier: Modifier,
    isWide: Boolean,
    deviceId: DeviceId,
    onPowerClicked: (DashboardVM.ModuleItem.Power) -> Unit,
    onPowerAlerts: (DeviceId) -> Unit,
    onWifiClicked: (DashboardVM.ModuleItem.Wifi) -> Unit,
    onWifiPermissionGrant: (Permission) -> Unit,
    onConnectivityClicked: (DashboardVM.ModuleItem.Connectivity) -> Unit,
    onAppsClicked: (DeviceId) -> Unit,
    onInstallLatestApp: (AppsInfo) -> Unit,
    onClipboardClicked: (DashboardVM.ModuleItem.Clipboard) -> Unit,
    onClearClipboard: () -> Unit,
    onShareClipboard: () -> Unit,
    onCopyClipboard: (ClipboardInfo) -> Unit,
    showMessage: (String) -> Unit,
) {
    when (val item = moduleMap[moduleId]) {
        is DashboardVM.ModuleItem.Power -> PowerModuleTile(
            state = PowerDashState(
                info = item.data.data,
                batteryLowAlert = item.batteryLowAlert,
                showSettings = item.showSettings,
            ),
            modifier = modifier,
            isWide = isWide,
            onDetailClicked = { onPowerClicked(item) },
            onSettingsClicked = { onPowerAlerts(deviceId) },
        )

        is DashboardVM.ModuleItem.Wifi -> WifiModuleTile(
            state = WifiDashState(
                info = item.data.data,
                showPermissionAction = item.showPermissionAction,
            ),
            modifier = modifier,
            isWide = isWide,
            onDetailClicked = { onWifiClicked(item) },
            onGrantPermission = onWifiPermissionGrant,
        )

        is DashboardVM.ModuleItem.Connectivity -> ConnectivityModuleTile(
            info = item.data.data,
            modifier = modifier,
            isWide = isWide,
            onDetailClicked = { onConnectivityClicked(item) },
        )

        is DashboardVM.ModuleItem.Apps -> AppsModuleTile(
            info = item.data.data,
            modifier = modifier,
            isWide = isWide,
            onAppsClicked = { onAppsClicked(deviceId) },
            onInstallClicked = { onInstallLatestApp(item.data.data) },
        )

        is DashboardVM.ModuleItem.Clipboard -> ClipboardModuleTile(
            state = ClipboardDashState(
                info = item.data.data,
                isOurDevice = item.isOurDevice,
            ),
            modifier = modifier,
            isWide = isWide,
            onDetailClicked = { onClipboardClicked(item) },
            onClearClicked = onClearClipboard,
            onShareClicked = onShareClipboard,
            onCopyClicked = { onCopyClipboard(item.data.data) },
            showMessage = showMessage,
        )

        null -> {} // Module not available for this device
    }
}

private fun buildModuleIdMap(moduleItems: List<DashboardVM.ModuleItem>): Map<String, DashboardVM.ModuleItem> {
    return moduleItems.associateBy { item ->
        when (item) {
            is DashboardVM.ModuleItem.Power -> "eu.darken.octi.module.core.power"
            is DashboardVM.ModuleItem.Wifi -> "eu.darken.octi.module.core.wifi"
            is DashboardVM.ModuleItem.Connectivity -> "eu.darken.octi.module.core.connectivity"
            is DashboardVM.ModuleItem.Apps -> "eu.darken.octi.module.core.apps"
            is DashboardVM.ModuleItem.Clipboard -> "eu.darken.octi.module.core.clipboard"
        }
    }
}
