package eu.darken.octi.main.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material.icons.twotone.CloudSync
import androidx.compose.material.icons.twotone.Coffee
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.clampToNow
import eu.darken.octi.common.compose.OctiMascot
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.permissions.descriptionRes
import eu.darken.octi.common.permissions.labelRes
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.main.ui.dashboard.editor.TileEditorCard
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.module.core.ModuleSync
import eu.darken.octi.modules.apps.AppsModule
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.ClipboardModule
import eu.darken.octi.modules.clipboard.ui.dashboard.ClipboardDetailSheet
import eu.darken.octi.modules.connectivity.ConnectivityModule
import eu.darken.octi.modules.connectivity.ui.dashboard.ConnectivityDetailSheet
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.ui.dashboard.PowerDetailSheet
import eu.darken.octi.modules.wifi.WifiModule
import eu.darken.octi.modules.wifi.ui.dashboard.WifiDetailSheet
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.sync.core.SyncConnector.EventMode
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncOrchestrator
import kotlinx.coroutines.launch
import java.time.Instant
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.syncs.gdrive.R as GDriveR
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.syncs.octiserver.R as OctiServerR
import eu.darken.octi.syncs.octiserver.ui.OctiServerIcon

@Composable
fun DashboardScreenHost(vm: DashboardVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    var showPermissionPopup by remember { mutableStateOf<DashboardEvent.ShowPermissionPopup?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.onPermissionResult(granted)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingPermission) {
            awaitingPermission = false
            vm.onPermissionResult(true)
        }
    }

    // Handle dashboard events
    LaunchedEffect(Unit) {
        vm.dashboardEvents.collect { event ->
            when (event) {
                is DashboardEvent.RequestPermissionEvent -> {
                    when (event.permission) {
                        Permission.IGNORE_BATTERY_OPTIMIZATION -> {
                            awaitingPermission = true
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                )
                            }
                        }

                        else -> permissionLauncher.launch(event.permission.permissionId)
                    }
                }

                is DashboardEvent.ShowPermissionDismissHint -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.permission_dismiss_hint))
                }

                is DashboardEvent.ShowPermissionPopup -> {
                    showPermissionPopup = event
                }

                is DashboardEvent.OpenAppOrStore -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (_: Exception) {
                        try {
                            context.startActivity(event.fallback)
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }
                }
            }
        }
    }

    // Initial refresh
    LaunchedEffect(Unit) {
        vm.refresh()
    }

    // Permission popup dialog
    showPermissionPopup?.let { popup ->
        PermissionPopupDialog(
            permission = popup.permission,
            onGrant = {
                popup.onGrant(it)
                showPermissionPopup = null
            },
            onDismiss = {
                popup.onDismiss(it)
                showPermissionPopup = null
            },
        )
    }

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        DashboardScreen(
            snackbarHostState = snackbarHostState,
            state = it,
            onRefresh = { vm.refresh() },
            onSyncServices = { vm.goToSyncServices() },
            onDegradedClick = { device ->
                val connectorId = device.degradedConnectorId
                if (connectorId != null) {
                    vm.goToDeviceDetails(connectorId, device.deviceId)
                } else {
                    vm.goToSyncServices()
                }
            },
            onConnectorDevices = { vm.goToConnectorDevices(it) },
            onUpgrade = { vm.goToUpgrade() },
            onSettings = { vm.goToSettings() },
            onDismissSyncSetup = { vm.dismissSyncSetup() },
            onSetupSync = { vm.setupSync() },
            onGrantPermission = { vm.requestPermission(it) },
            onDismissPermission = { vm.dismissPermission(it) },
            onDismissUpdate = { vm.dismissUpdate() },
            onViewUpdate = { vm.viewUpdate() },
            onStartUpdate = { vm.startUpdate() },
            onToggleSyncExpanded = { vm.toggleSyncExpanded() },
            onToggleDeviceCollapsed = { vm.toggleDeviceCollapsed(it) },
            onPowerAlerts = { vm.goToPowerAlerts(it) },
            onAppsList = { vm.goToAppsList(it) },
            onInstallLatestApp = { vm.onInstallLatestApp(it) },
            onClearClipboard = { vm.clearClipboard() },
            onShareClipboard = { vm.shareCurrentClipboard() },
            onCopyClipboard = { vm.setOsClipboard(it) },
            onWifiPermissionGrant = { vm.requestPermission(it) },
            onSaveTileLayout = { deviceId, config -> vm.saveTileLayout(deviceId, config) },
            onSaveAsDefaultTileLayout = { vm.saveAsDefaultTileLayout(it) },
            onResetTileLayout = { vm.resetTileLayout(it) },
            onMoveDeviceUp = { vm.moveDeviceUp(it) },
            onMoveDeviceDown = { vm.moveDeviceDown(it) },
        )
    }
}

@Composable
fun DashboardScreen(
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    state: DashboardVM.State,
    onRefresh: () -> Unit,
    onSyncServices: () -> Unit,
    onDegradedClick: (DashboardVM.DeviceItem) -> Unit,
    onConnectorDevices: (ConnectorId) -> Unit,
    onUpgrade: () -> Unit,
    onSettings: () -> Unit,
    onDismissSyncSetup: () -> Unit,
    onSetupSync: () -> Unit,
    onGrantPermission: (Permission) -> Unit,
    onDismissPermission: (Permission) -> Unit,
    onDismissUpdate: () -> Unit,
    onViewUpdate: () -> Unit,
    onStartUpdate: () -> Unit,
    onToggleSyncExpanded: () -> Unit,
    onToggleDeviceCollapsed: (String) -> Unit,
    onPowerAlerts: (DeviceId) -> Unit,
    onAppsList: (DeviceId) -> Unit,
    onInstallLatestApp: (AppsInfo) -> Unit,
    onClearClipboard: () -> Unit,
    onShareClipboard: () -> Unit,
    onCopyClipboard: (ClipboardInfo) -> Unit,
    onWifiPermissionGrant: (Permission) -> Unit,
    onSaveTileLayout: (String, TileLayoutConfig) -> Unit = { _, _ -> },
    onSaveAsDefaultTileLayout: (TileLayoutConfig) -> Unit = {},
    onResetTileLayout: (String) -> Unit = {},
    onMoveDeviceUp: (String) -> Unit = {},
    onMoveDeviceDown: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
    val offlineMessage = stringResource(CommonR.string.general_internal_not_available_msg)

    // Edit mode state
    var editingDeviceId by rememberSaveable { mutableStateOf<String?>(null) }

    // Auto-exit edit mode if the edited device vanishes
    LaunchedEffect(state.devices, editingDeviceId) {
        if (editingDeviceId != null && state.devices.none { it.deviceId.id == editingDeviceId }) {
            editingDeviceId = null
        }
    }

    // Detail dialog state
    var showPowerDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Power?>(null) }
    var showWifiDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Wifi?>(null) }
    var showConnectivityDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Connectivity?>(null) }
    var showClipboardDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Clipboard?>(null) }
    var showIssuesSheetSeverity by remember { mutableStateOf<IssueSeverity?>(null) }
    var showReliabilitySheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.isOffline) {
        if (state.isOffline) {
            snackbarHostState.showSnackbar(offlineMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        DashboardToolbarTitle(upgradeInfo = state.upgradeInfo)
                        val subtitle = dashboardSubtitle(state)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSyncServices) {
                        Icon(
                            imageVector = Icons.TwoTone.CloudSync,
                            contentDescription = stringResource(R.string.sync_services_label),
                        )
                    }
                    if (!state.upgradeInfo.isPro) {
                        IconButton(onClick = onUpgrade) {
                            Icon(
                                imageVector = Icons.TwoTone.Stars,
                                contentDescription = stringResource(CommonR.string.general_upgrade_action),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.TwoTone.Settings,
                            contentDescription = stringResource(R.string.label_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val columns = when {
                maxWidth >= 900.dp -> 3
                maxWidth >= 600.dp -> 2
                else -> 1
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            // Sync status bar (full-span)
            state.syncStatus?.let { syncStatus ->
                item(key = "sync_status", span = { GridItemSpan(maxLineSpan) }) {
                    SyncStatusBar(
                        syncStatus = syncStatus,
                        isExpanded = state.isSyncExpanded,
                        onToggleExpanded = onToggleSyncExpanded,
                        onRefresh = onRefresh,
                    )
                }
            }

            // Action chips row (sync setup + permissions + upgrade + issue counts)
            val errorCount = state.issues.count { it.severity == IssueSeverity.ERROR }
            val warningCount = state.issues.count { it.severity == IssueSeverity.WARNING }
            val hasActionChips = state.showSyncSetup || state.missingPermissions.isNotEmpty() || !state.upgradeInfo.isPro || errorCount > 0 || warningCount > 0
            if (hasActionChips) {
                item(key = "action_chips", span = { GridItemSpan(maxLineSpan) }) {
                    ActionChipsRow(
                        showSyncSetup = state.showSyncSetup,
                        missingPermissions = state.missingPermissions,
                        showUpgrade = !state.upgradeInfo.isPro,
                        errorCount = errorCount,
                        warningCount = warningCount,
                        onSetupSync = onSetupSync,
                        onDismissSyncSetup = onDismissSyncSetup,
                        onGrantPermission = { permission ->
                            if (permission == Permission.IGNORE_BATTERY_OPTIMIZATION) {
                                showReliabilitySheet = true
                            } else {
                                onGrantPermission(permission)
                            }
                        },
                        onDismissPermission = onDismissPermission,
                        onUpgrade = onUpgrade,
                        onErrorsClick = { showIssuesSheetSeverity = IssueSeverity.ERROR },
                        onWarningsClick = { showIssuesSheetSeverity = IssueSeverity.WARNING },
                    )
                }
            }

            // Update card (full-span)
            if (state.update != null) {
                item(key = "update", span = { GridItemSpan(maxLineSpan) }) {
                    UpdateCard(
                        update = state.update,
                        onDismiss = onDismissUpdate,
                        onViewUpdate = onViewUpdate,
                        onUpdate = onStartUpdate,
                    )
                }
            }

            // Device cards
            val devices = state.devices
            val deviceLimit = state.deviceLimit
            val showLimitCard = state.deviceLimitReached
            val freeDevices = if (showLimitCard) devices.take(deviceLimit) else devices

            items(
                items = freeDevices,
                key = { it.deviceId.id },
            ) { device ->
                val index = devices.indexOf(device)
                DeviceCardOrEditor(
                    device = device,
                    editingDeviceId = editingDeviceId,
                    isFirst = index == 0,
                    isLast = index == devices.lastIndex,
                    onToggleCollapse = onToggleDeviceCollapsed,
                    onEditCard = { editingDeviceId = it },
                    onUpgrade = onUpgrade,
                    onManageStaleDevice = onSyncServices,
                    onDegradedClick = onDegradedClick,
                    onPowerClicked = { showPowerDetail = it },
                    onPowerAlerts = onPowerAlerts,
                    onWifiClicked = { showWifiDetail = it },
                    onWifiPermissionGrant = onWifiPermissionGrant,
                    onConnectivityClicked = { showConnectivityDetail = it },
                    onAppsClicked = onAppsList,
                    onInstallLatestApp = onInstallLatestApp,
                    onClipboardClicked = { showClipboardDetail = it },
                    onClearClipboard = onClearClipboard,
                    onShareClipboard = onShareClipboard,
                    onCopyClipboard = onCopyClipboard,
                    showMessage = showMessage,
                    onDoneEditing = { deviceId, config ->
                        onSaveTileLayout(deviceId, config)
                        editingDeviceId = null
                    },
                    onCancelEditing = { editingDeviceId = null },
                    onResetTileLayout = onResetTileLayout,
                    onSaveAsDefault = onSaveAsDefaultTileLayout,
                    onMoveUp = onMoveDeviceUp,
                    onMoveDown = onMoveDeviceDown,
                )
            }

            // Device limit card (full-span)
            if (showLimitCard) {
                item(key = "device_limit", span = { GridItemSpan(maxLineSpan) }) {
                    DeviceLimitCard(
                        current = devices.size,
                        maximum = deviceLimit,
                        onManageDevices = onSyncServices,
                        onUpgrade = onUpgrade,
                    )
                }

                items(
                    items = devices.drop(deviceLimit),
                    key = { it.deviceId.id },
                ) { device ->
                    val index = devices.indexOf(device)
                    DeviceCardOrEditor(
                        device = device,
                        editingDeviceId = editingDeviceId,
                        isFirst = false,
                        isLast = index == devices.lastIndex,
                        onToggleCollapse = onToggleDeviceCollapsed,
                        onEditCard = { editingDeviceId = it },
                        onUpgrade = onUpgrade,
                        onManageStaleDevice = onSyncServices,
                        onDegradedClick = onDegradedClick,
                        onPowerClicked = { showPowerDetail = it },
                        onPowerAlerts = onPowerAlerts,
                        onWifiClicked = { showWifiDetail = it },
                        onWifiPermissionGrant = onWifiPermissionGrant,
                        onConnectivityClicked = { showConnectivityDetail = it },
                        onAppsClicked = onAppsList,
                        onInstallLatestApp = onInstallLatestApp,
                        onClipboardClicked = { showClipboardDetail = it },
                        onClearClipboard = onClearClipboard,
                        onShareClipboard = onShareClipboard,
                        onCopyClipboard = onCopyClipboard,
                        showMessage = showMessage,
                        onDoneEditing = { deviceId, config ->
                            onSaveTileLayout(deviceId, config)
                            editingDeviceId = null
                        },
                        onCancelEditing = { editingDeviceId = null },
                        onResetTileLayout = onResetTileLayout,
                        onSaveAsDefault = onSaveAsDefaultTileLayout,
                        onMoveUp = onMoveDeviceUp,
                        onMoveDown = onMoveDeviceDown,
                    )
                }
            }

        }
        }
    }

    // Detail sheets
    showPowerDetail?.let { item ->
        PowerDetailSheet(
            info = item.data.data,
            onDismiss = { showPowerDetail = null },
        )
    }

    showWifiDetail?.let { item ->
        WifiDetailSheet(
            info = item.data.data,
            onDismiss = { showWifiDetail = null },
        )
    }

    showConnectivityDetail?.let { item ->
        ConnectivityDetailSheet(
            info = item.data.data,
            onDismiss = { showConnectivityDetail = null },
            showMessage = showMessage,
        )
    }

    showClipboardDetail?.let { item ->
        ClipboardDetailSheet(
            info = item.data.data,
            onDismiss = { showClipboardDetail = null },
            onCopy = onCopyClipboard,
            showMessage = showMessage,
        )
    }

    showIssuesSheetSeverity?.let { severity ->
        val filteredIssues = state.issues.filter { it.severity == severity }
        if (filteredIssues.isNotEmpty()) {
            val connectorLabels = state.syncStatus?.syncDetail?.connectors
                ?.associate { it.connectorId to it.accountLabel }
                ?: emptyMap()
            IssuesSummarySheet(
                severity = severity,
                issues = filteredIssues,
                connectorLabels = connectorLabels,
                onConnectorClick = { connectorId ->
                    showIssuesSheetSeverity = null
                    onConnectorDevices(connectorId)
                },
                onDismiss = { showIssuesSheetSeverity = null },
            )
        } else {
            showIssuesSheetSeverity = null
        }
    }

    if (showReliabilitySheet) {
        ReliabilitySheet(
            onGoToSettings = {
                showReliabilitySheet = false
                onGrantPermission(Permission.IGNORE_BATTERY_OPTIMIZATION)
            },
            onDismissPermission = {
                showReliabilitySheet = false
                onDismissPermission(Permission.IGNORE_BATTERY_OPTIMIZATION)
            },
            onDismiss = { showReliabilitySheet = false },
        )
    }
}

@Composable
private fun dashboardSubtitle(state: DashboardVM.State): String? {
    if (state.deviceCount <= 0) {
        return if (BuildConfigWrap.BUILD_TYPE == BuildConfigWrap.BuildType.RELEASE) {
            "v${BuildConfigWrap.VERSION_NAME}"
        } else {
            BuildConfigWrap.VERSION_DESCRIPTION
        }
    }
    val deviceQuantity = pluralStringResource(
        R.plurals.general_devices_count_label,
        state.deviceCount,
        state.deviceCount,
    )
    return if (BuildConfigWrap.DEBUG) {
        "$deviceQuantity ${BuildConfigWrap.GIT_SHA}"
    } else {
        deviceQuantity
    }
}

@Composable
private fun connectorTypesLabel(
    syncStatus: DashboardVM.SyncStatus,
): String? {
    val connectorTypes = syncStatus.connectorTypes
    if (connectorTypes.isEmpty()) return null
    val connectorsByType = syncStatus.syncDetail.connectors.groupBy { it.type }
    val names = connectorTypes.map { type ->
        val typeName = when (type) {
            ConnectorType.GDRIVE -> stringResource(GDriveR.string.sync_gdrive_type_label)
            ConnectorType.OCTISERVER -> stringResource(OctiServerR.string.sync_octiserver_type_label)
        }
        val count = connectorsByType[type]?.size ?: 1
        if (count > 1) {
            stringResource(R.string.dashboard_sync_connector_type_count, typeName, count)
        } else {
            typeName
        }
    }
    val base = stringResource(R.string.dashboard_sync_status_via, names.joinToString(", "))

    val orchestratorState = syncStatus.orchestratorState
    val activeModes = orchestratorState.quickSync.connectorModes.values.distinct()
    if (activeModes.isNotEmpty()) {
        val modeLabel = when {
            activeModes.size == 1 && activeModes.first() == SyncConnector.EventMode.LIVE ->
                stringResource(R.string.dashboard_sync_status_mode_live)

            activeModes.size == 1 && activeModes.first() == SyncConnector.EventMode.POLLING ->
                stringResource(R.string.dashboard_sync_status_mode_fast)

            else -> stringResource(R.string.dashboard_sync_status_mode_quicksync)
        }
        return "$base \u00B7 $modeLabel"
    }

    val nextRun = orchestratorState.backgroundSync.defaultWorker.nextRunAt
    if (nextRun != null) {
        val minutes = java.time.Duration.between(syncStatus.now, nextRun).toMinutes()
        if (minutes > 0) {
            return "$base \u00B7 ${stringResource(R.string.dashboard_sync_status_next_in, "${minutes}m")}"
        } else {
            return "$base \u00B7 ${stringResource(R.string.dashboard_sync_status_due)}"
        }
    }

    return base
}

@Composable
private fun SyncStatusBar(
    syncStatus: DashboardVM.SyncStatus,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRefresh: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
    ) {
        Box {
            Column {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpanded)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OctiMascot(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        when (syncStatus) {
                            is DashboardVM.SyncStatus.Syncing -> {
                                val moduleNames = syncStatus.syncingModules
                                    .sortedBy {
                                        MODULE_DISPLAY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
                                    }
                                    .mapNotNull { moduleIdToStringRes(it) }
                                    .map { stringResource(it) }
                                val text = if (moduleNames.isNotEmpty()) {
                                    stringResource(
                                        R.string.dashboard_sync_status_syncing_modules,
                                        moduleNames.joinToString(", ")
                                    )
                                } else {
                                    stringResource(R.string.dashboard_sync_status_syncing)
                                }
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            is DashboardVM.SyncStatus.Idle -> {
                                val primaryText = syncStatus.lastSyncAt?.let {
                                    stringResource(
                                        R.string.dashboard_sync_status_last_synced,
                                        DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString(),
                                    )
                                } ?: stringResource(R.string.dashboard_sync_status_never)
                                Text(
                                    text = primaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            is DashboardVM.SyncStatus.Error -> Text(
                                text = stringResource(R.string.dashboard_sync_status_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        val secondaryText = when (syncStatus) {
                            is DashboardVM.SyncStatus.Error -> syncStatus.message
                                ?: connectorTypesLabel(syncStatus)

                            else -> connectorTypesLabel(syncStatus)
                        }
                        if (secondaryText != null) {
                            Text(
                                text = secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (syncStatus) {
                            is DashboardVM.SyncStatus.Syncing -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }

                            else -> {
                                IconButton(onClick = onRefresh) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Refresh,
                                        contentDescription = stringResource(R.string.general_sync_action),
                                    )
                                }
                            }
                        }
                    }
                }

                // Expanded detail
                if (isExpanded) {
                    val detail = syncStatus.syncDetail
                    HorizontalDivider()

                    // Module rows
                    Spacer(modifier = Modifier.height(4.dp))
                    val sortedModules = detail.modules.sortedBy {
                        MODULE_DISPLAY_ORDER.indexOf(it.moduleId).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
                    }
                    sortedModules.forEach { moduleState ->
                        SyncDetailModuleRow(moduleState)
                    }

                    // Backend rows
                    if (detail.connectors.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(4.dp))
                        detail.connectors.forEach { connector ->
                            SyncDetailConnectorRow(
                                connector = connector,
                                quickSyncMode = syncStatus.orchestratorState.quickSync.connectorModes[connector.connectorId],
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Overlapping bottom-center chevron
            Icon(
                imageVector = if (isExpanded) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncDetailModuleRow(moduleState: ModuleManager.ModuleSyncState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = moduleIdToIcon(moduleState.moduleId)
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(10.dp))
        val nameRes = moduleIdToStringRes(moduleState.moduleId)
        Text(
            text = nameRes?.let { stringResource(it) } ?: moduleState.moduleId.id,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        val isActive = moduleState.activity != ModuleSync.SyncActivity.IDLE
        val statusText = when (moduleState.activity) {
            ModuleSync.SyncActivity.IDLE -> stringResource(R.string.dashboard_sync_detail_idle)
            ModuleSync.SyncActivity.READING -> stringResource(R.string.dashboard_sync_detail_reading)
            ModuleSync.SyncActivity.WRITING -> stringResource(R.string.dashboard_sync_detail_writing)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        if (isActive) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                imageVector = Icons.TwoTone.Coffee,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncDetailConnectorRow(
    connector: DashboardVM.ConnectorDetail,
    quickSyncMode: SyncConnector.EventMode? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = when (connector.type) {
            ConnectorType.GDRIVE -> stringResource(GDriveR.string.sync_gdrive_type_label)
            ConnectorType.OCTISERVER -> stringResource(OctiServerR.string.sync_octiserver_type_label)
        }
        when (connector.type) {
            ConnectorType.GDRIVE -> Icon(
                painter = painterResource(R.drawable.ic_baseline_gdrive_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ConnectorType.OCTISERVER -> OctiServerIcon(modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = connector.accountLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connector.isBusy) {
            Text(
                text = stringResource(R.string.dashboard_sync_status_syncing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )
        } else if (quickSyncMode != null && quickSyncMode != SyncConnector.EventMode.NONE) {
            val modeLabel = when (quickSyncMode) {
                SyncConnector.EventMode.LIVE -> stringResource(R.string.dashboard_sync_status_mode_live)
                SyncConnector.EventMode.POLLING -> stringResource(R.string.dashboard_sync_status_mode_fast)
                SyncConnector.EventMode.NONE -> error("Filtered above")
            }
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.TwoTone.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = stringResource(R.string.dashboard_sync_detail_idle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.TwoTone.Coffee,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val MODULE_DISPLAY_ORDER = listOf(
    PowerModule.MODULE_ID,
    ConnectivityModule.MODULE_ID,
    WifiModule.MODULE_ID,
    ClipboardModule.MODULE_ID,
    AppsModule.MODULE_ID,
    MetaModule.MODULE_ID,
)

private fun moduleIdToIcon(moduleId: ModuleId): ImageVector = when (moduleId) {
    PowerModule.MODULE_ID -> Icons.TwoTone.BatteryFull
    WifiModule.MODULE_ID -> Icons.TwoTone.Wifi
    AppsModule.MODULE_ID -> Icons.TwoTone.Apps
    ClipboardModule.MODULE_ID -> Icons.TwoTone.ContentPaste
    ConnectivityModule.MODULE_ID -> Icons.TwoTone.CellTower
    MetaModule.MODULE_ID -> Icons.TwoTone.Info
    else -> Icons.TwoTone.QuestionMark
}

private fun moduleIdToStringRes(moduleId: ModuleId): Int? = when (moduleId) {
    PowerModule.MODULE_ID -> PowerR.string.module_power_label
    WifiModule.MODULE_ID -> WifiR.string.module_wifi_label
    AppsModule.MODULE_ID -> AppsR.string.module_apps_label
    ClipboardModule.MODULE_ID -> ClipboardR.string.module_clipboard_label
    ConnectivityModule.MODULE_ID -> ConnectivityR.string.module_connectivity_label
    MetaModule.MODULE_ID -> MetaR.string.module_meta_label
    else -> null
}

@Composable
private fun DashboardToolbarTitle(upgradeInfo: UpgradeRepo.Info) {
    val appName = stringResource(CommonR.string.app_name)
    val titleText = when {
        upgradeInfo.isPro -> stringResource(R.string.app_name_upgraded)
        else -> appName
    }

    val titleParts = titleText.split(" ").filter { it.isNotEmpty() }

    if (titleParts.size == 2) {
        Text(
            text = buildAnnotatedString {
                append("${titleParts[0]} ")
                withStyle(SpanStyle(color = colorResource(R.color.colorUpgraded))) {
                    append(titleParts[1])
                }
            },
        )
    } else {
        Text(text = appName)
    }
}

@Composable
private fun ActionChipsRow(
    showSyncSetup: Boolean,
    missingPermissions: List<Permission>,
    showUpgrade: Boolean,
    errorCount: Int = 0,
    warningCount: Int = 0,
    onSetupSync: () -> Unit,
    onDismissSyncSetup: () -> Unit,
    onGrantPermission: (Permission) -> Unit,
    onDismissPermission: (Permission) -> Unit,
    onUpgrade: () -> Unit,
    onErrorsClick: () -> Unit = {},
    onWarningsClick: () -> Unit = {},
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSyncSetup) {
            ActionChip(
                icon = Icons.TwoTone.CloudSync,
                label = stringResource(R.string.onboarding_setupsync_title),
                onClick = onSetupSync,
                onLongClick = onDismissSyncSetup,
            )
        }
        missingPermissions.forEach { permission ->
            ActionChip(
                icon = when (permission) {
                    Permission.IGNORE_BATTERY_OPTIMIZATION -> Icons.TwoTone.BatteryFull
                    Permission.POST_NOTIFICATIONS -> Icons.TwoTone.Info
                    else -> Icons.TwoTone.Info
                },
                label = stringResource(
                    when (permission) {
                        Permission.IGNORE_BATTERY_OPTIMIZATION -> R.string.permission_reliability_label
                        Permission.POST_NOTIFICATIONS -> R.string.permission_notifications_post_label
                        else -> R.string.permission_required_label
                    }
                ),
                onClick = { onGrantPermission(permission) },
                onLongClick = { onDismissPermission(permission) },
            )
        }
        if (showUpgrade) {
            ActionChip(
                icon = Icons.TwoTone.Stars,
                label = stringResource(CommonR.string.general_upgrade_action),
                onClick = onUpgrade,
                onLongClick = null,
            )
        }
        if (errorCount > 0) {
            ActionChip(
                icon = Icons.TwoTone.Warning,
                label = pluralStringResource(R.plurals.sync_issues_errors_count, errorCount, errorCount),
                onClick = onErrorsClick,
                onLongClick = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        if (warningCount > 0) {
            ActionChip(
                icon = Icons.TwoTone.Warning,
                label = pluralStringResource(R.plurals.sync_issues_warnings_count, warningCount, warningCount),
                onClick = onWarningsClick,
                onLongClick = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// region Cards

@Composable
private fun SyncSetupCard(
    onDismiss: () -> Unit,
    onSetup: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.onboarding_setupsync_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_setupsync_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonR.string.general_dismiss_action))
                }
                TextButton(onClick = onSetup) {
                    Text(stringResource(R.string.onboarding_setupsync_title))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: Permission,
    onGrant: (Permission) -> Unit,
    onDismiss: (Permission) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(permission.labelRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(permission.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onDismiss(permission) }) {
                    Text(stringResource(CommonR.string.general_dismiss_action))
                }
                TextButton(onClick = { onGrant(permission) }) {
                    Text(stringResource(R.string.permission_grant_action))
                }
            }
        }
    }
}

@Composable
private fun PermissionPopupDialog(
    permission: Permission,
    onGrant: (Permission) -> Unit,
    onDismiss: (Permission) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss(permission) },
        title = { Text(stringResource(permission.labelRes)) },
        text = { Text(stringResource(permission.descriptionRes)) },
        confirmButton = {
            TextButton(onClick = { onGrant(permission) }) {
                Text(stringResource(R.string.permission_grant_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(permission) }) {
                Text(stringResource(CommonR.string.general_dismiss_action))
            }
        },
    )
}

@Composable
private fun UpdateCard(
    update: eu.darken.octi.main.core.updater.UpdateChecker.Update,
    onDismiss: () -> Unit,
    onViewUpdate: () -> Unit,
    onUpdate: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onViewUpdate),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.updates_dashcard_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.updates_dashcard_body,
                    "v${BuildConfigWrap.VERSION_NAME}",
                    update.versionName,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonR.string.general_dismiss_action))
                }
                TextButton(onClick = onUpdate) {
                    Text(stringResource(CommonR.string.general_update_action))
                }
            }
        }
    }
}

@Composable
private fun DeviceLimitCard(
    current: Int,
    maximum: Int,
    onManageDevices: () -> Unit,
    onUpgrade: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.pro_device_limit_reached_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val body = buildString {
                append(pluralStringResource(R.plurals.pro_device_limit_current_description, current, current))
                append(" ")
                append(pluralStringResource(R.plurals.pro_device_limit_maximum_description, maximum, maximum))
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onManageDevices) {
                    Text(stringResource(R.string.general_manage_devices_action))
                }
                TextButton(onClick = onUpgrade) {
                    Text(stringResource(CommonR.string.general_upgrade_action))
                }
            }
        }
    }
}

@Composable
private fun UpgradeCard(
    deviceLimit: Int,
    onUpgrade: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onUpgrade),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Stars,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.upgrades_dashcard_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.upgrades_dashcard_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pluralStringResource(R.plurals.upgrades_dashcard_device_limit_hint, deviceLimit, deviceLimit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onUpgrade) {
                    Text(stringResource(R.string.upgrades_dashcard_upgrade_action))
                }
            }
        }
    }
}

// endregion

// region Device Card

@Composable
private fun DashboardDeviceCard(
    device: DashboardVM.DeviceItem,
    onToggleCollapse: (String) -> Unit,
    onEditCard: (String) -> Unit,
    onUpgrade: () -> Unit,
    onManageStaleDevice: () -> Unit,
    onDegradedClick: (DashboardVM.DeviceItem) -> Unit,
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
    val meta = device.meta?.data
    val isDegraded = device.isDegraded
    val hasModules = device.moduleItems.isNotEmpty()
    val canEdit = hasModules && !device.isLimited && !isDegraded
    val shouldShowModules = !device.isLimited && hasModules && !device.isCollapsed && !isDegraded

    val chevronRotation by animateFloatAsState(
        targetValue = if (device.isCollapsed) 0f else 90f,
        label = "chevronRotation",
    )

    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        colors = if (isDegraded) {
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Clickable area (icon, name, chevron)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = {
                            when {
                                isDegraded -> onDegradedClick(device)
                                canEdit -> onToggleCollapse(device.deviceId.id)
                                device.isLimited -> onUpgrade()
                            }
                        },
                        onLongClick = {
                            if (canEdit) {
                                onEditCard(device.deviceId.id)
                            }
                        },
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        device.isCurrentDevice -> Icons.TwoTone.Home
                        isDegraded -> Icons.TwoTone.Warning
                        else -> when (meta?.deviceType) {
                            MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                            MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                            else -> Icons.TwoTone.QuestionMark
                        }
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (isDegraded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isDegraded && device.meta != null) {
                        val primaryInfo = device.infos.firstOrNull()
                        val badgeColor = primaryInfo?.severityColor()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (badgeColor != null) {
                                Icon(
                                    imageVector = Icons.TwoTone.Warning,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = DateUtils.getRelativeTimeSpanString(
                                    device.meta.modifiedAt.clampToNow().toEpochMilli()
                                ).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor ?: Color.Unspecified,
                            )
                        }
                    } else if (isDegraded) {
                        val details = listOfNotNull(device.degradedVersion, device.degradedPlatform).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                if (canEdit) {
                    Icon(
                        imageVector = Icons.TwoTone.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(chevronRotation),
                    )
                } else if (device.isLimited) {
                    Icon(
                        imageVector = Icons.TwoTone.Stars,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Overflow menu (outside combinedClickable to avoid gesture conflicts)
            if (canEdit) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(R.string.dashboard_device_edit_card_action),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dashboard_device_edit_card_action)) },
                            onClick = {
                                showMenu = false
                                onEditCard(device.deviceId.id)
                            },
                        )
                    }
                }
            }
        }

        // Info rows (shown for both normal expanded and degraded devices)
        if (shouldShowModules || isDegraded) {
            device.infos.forEach { issue ->
                HorizontalDivider()
                IssueInfoRow(
                    issue = issue,
                    onClick = { onDegradedClick(device) },
                )
            }
        }

        // Module tiles
        if (shouldShowModules) {
            val availableModuleIds = buildAvailableModuleIds(device.moduleItems)
            val rows = device.tileLayout.toRows(availableModuleIds)

            ModuleTileGrid(
                rows = rows,
                moduleItems = device.moduleItems,
                deviceId = device.deviceId,
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

@Composable
private fun DeviceCardOrEditor(
    device: DashboardVM.DeviceItem,
    editingDeviceId: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleCollapse: (String) -> Unit,
    onEditCard: (String) -> Unit,
    onUpgrade: () -> Unit,
    onManageStaleDevice: () -> Unit,
    onDegradedClick: (DashboardVM.DeviceItem) -> Unit,
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
    onDoneEditing: (String, TileLayoutConfig) -> Unit,
    onCancelEditing: () -> Unit,
    onResetTileLayout: (String) -> Unit,
    onSaveAsDefault: (TileLayoutConfig) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
) {
    val deviceId = device.deviceId.id
    if (editingDeviceId == deviceId) {
        TileEditorCard(
            deviceName = device.displayLabel,
            initialConfig = device.tileLayout,
            onDone = { config -> onDoneEditing(deviceId, config) },
            onCancel = onCancelEditing,
            onReset = { onResetTileLayout(deviceId) },
            onSaveAsDefault = onSaveAsDefault,
            isFirst = isFirst,
            isLast = isLast,
            onMoveUp = { onMoveUp(deviceId) },
            onMoveDown = { onMoveDown(deviceId) },
        )
    } else {
        DashboardDeviceCard(
            device = device,
            onToggleCollapse = onToggleCollapse,
            onEditCard = onEditCard,
            onUpgrade = onUpgrade,
            onManageStaleDevice = onManageStaleDevice,
            onDegradedClick = onDegradedClick,
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

private fun buildAvailableModuleIds(moduleItems: List<DashboardVM.ModuleItem>): Set<String> {
    return moduleItems.mapNotNull { item ->
        when (item) {
            is DashboardVM.ModuleItem.Power -> "eu.darken.octi.module.core.power"
            is DashboardVM.ModuleItem.Wifi -> "eu.darken.octi.module.core.wifi"
            is DashboardVM.ModuleItem.Connectivity -> "eu.darken.octi.module.core.connectivity"
            is DashboardVM.ModuleItem.Apps -> "eu.darken.octi.module.core.apps"
            is DashboardVM.ModuleItem.Clipboard -> "eu.darken.octi.module.core.clipboard"
        }
    }.toSet()
}

@Composable
private fun ConnectorIssue.severityColor(): Color = when (severity) {
    IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
    IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun IssueInfoRow(
    issue: ConnectorIssue,
    onClick: () -> Unit,
) {
    val tint = issue.severityColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = issue.label.get(LocalContext.current),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.weight(1f),
        )
    }
}

// endregion

// region Previews

private fun previewOrchestratorState() = SyncOrchestrator.State(
    quickSync = SyncOrchestrator.QuickSyncState(
        isActive = false,
        connectorModes = emptyMap(),
    ),
    backgroundSync = SyncOrchestrator.BackgroundSyncState(
        defaultWorker = SyncOrchestrator.BackgroundSyncState.WorkerInfo(
            isEnabled = true,
            isRunning = false,
            isBlocked = false,
            nextRunAt = null,
        ),
        chargingWorker = SyncOrchestrator.BackgroundSyncState.WorkerInfo(
            isEnabled = false,
            isRunning = false,
            isBlocked = false,
            nextRunAt = null,
        ),
    ),
)

private data class PreviewUpgradeInfo(
    override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS,
    override val isPro: Boolean = false,
    override val upgradedAt: Instant? = null,
) : UpgradeRepo.Info

@Preview2
@Composable
private fun DashboardScreenEmptyPreview() = PreviewWrapper {
    DashboardScreen(
        state = DashboardVM.State(
            devices = emptyList(),
            deviceCount = 0,
            syncStatus = null,
            isOffline = false,
            showSyncSetup = true,
            missingPermissions = emptyList(),
            update = null,
            upgradeInfo = PreviewUpgradeInfo(),
            deviceLimitReached = false,
        ),
        onRefresh = {},
        onSyncServices = {},
        onDegradedClick = {},
        onConnectorDevices = {},
        onUpgrade = {},
        onSettings = {},
        onDismissSyncSetup = {},
        onSetupSync = {},
        onGrantPermission = {},
        onDismissPermission = {},
        onDismissUpdate = {},
        onViewUpdate = {},
        onStartUpdate = {},
        onToggleSyncExpanded = {},
        onToggleDeviceCollapsed = {},
        onPowerAlerts = {},
        onAppsList = {},
        onInstallLatestApp = {},
        onClearClipboard = {},
        onShareClipboard = {},
        onCopyClipboard = {},
        onWifiPermissionGrant = {},
    )
}

@Preview2
@Composable
private fun DashboardScreenPreview() = PreviewWrapper {
    val now = Instant.now()
    val deviceId = DeviceId("preview-device-1")

    DashboardScreen(
        state = DashboardVM.State(
            devices = listOf(
                DashboardVM.DeviceItem(
                    now = now,
                    deviceId = deviceId,
                    meta = ModuleData(
                        modifiedAt = now.minusSeconds(300),
                        deviceId = deviceId,
                        moduleId = ModuleId("meta"),
                        data = MetaInfo(
                            deviceLabel = "Pixel 8",
                            deviceId = deviceId,
                            octiVersionName = "0.14.0",
                            octiGitSha = "abc1234",
                            deviceManufacturer = "Google",
                            deviceName = "Pixel 8",
                            deviceType = MetaInfo.DeviceType.PHONE,
                            deviceBootedAt = now.minusSeconds(86400),
                            androidVersionName = "14",
                            androidApiLevel = 34,
                            androidSecurityPatch = "2024-01-05",
                        ),
                    ),
                    moduleItems = listOf(
                        DashboardVM.ModuleItem.Power(
                            data = ModuleData(
                                modifiedAt = now.minusSeconds(60),
                                deviceId = deviceId,
                                moduleId = ModuleId("power"),
                                data = PowerInfo(
                                    status = Status.DISCHARGING,
                                    battery = PowerInfo.Battery(
                                        level = 75,
                                        scale = 100,
                                        health = 2,
                                        temp = 28.5f,
                                    ),
                                    chargeIO = ChargeIO(
                                        currentNow = null,
                                        currenAvg = null,
                                        fullSince = null,
                                        fullAt = null,
                                        emptyAt = null,
                                    ),
                                ),
                            ),
                            batteryLowAlert = null,
                            showSettings = true,
                        ),
                    ),
                    isCollapsed = false,
                    isLimited = false,
                    isCurrentDevice = false,
                ),
            ),
            deviceCount = 1,
            syncStatus = DashboardVM.SyncStatus.Idle(
                lastSyncAt = now.minusSeconds(300),
                connectorTypes = listOf(ConnectorType.GDRIVE),
                syncDetail = DashboardVM.SyncDetail(modules = emptyList(), connectors = emptyList()),
                orchestratorState = previewOrchestratorState(),
                now = now,
            ),
            isOffline = false,
            showSyncSetup = false,
            missingPermissions = emptyList(),
            update = null,
            upgradeInfo = PreviewUpgradeInfo(isPro = true),
            deviceLimitReached = false,
        ),
        onRefresh = {},
        onSyncServices = {},
        onDegradedClick = {},
        onConnectorDevices = {},
        onUpgrade = {},
        onSettings = {},
        onDismissSyncSetup = {},
        onSetupSync = {},
        onGrantPermission = {},
        onDismissPermission = {},
        onDismissUpdate = {},
        onViewUpdate = {},
        onStartUpdate = {},
        onToggleSyncExpanded = {},
        onToggleDeviceCollapsed = {},
        onPowerAlerts = {},
        onAppsList = {},
        onInstallLatestApp = {},
        onClearClipboard = {},
        onShareClipboard = {},
        onCopyClipboard = {},
        onWifiPermissionGrant = {},
    )
}

@Preview2
@Composable
private fun DashboardScreenMultiDevicePreview() = PreviewWrapper {
    val now = Instant.now()
    val deviceId1 = DeviceId("preview-device-1")
    val deviceId2 = DeviceId("preview-device-2")

    fun previewDevice(
        deviceId: DeviceId,
        label: String,
        type: MetaInfo.DeviceType,
        batteryLevel: Int,
        isCollapsed: Boolean,
    ) = DashboardVM.DeviceItem(
        now = now,
        deviceId = deviceId,
        meta = ModuleData(
            modifiedAt = now.minusSeconds(300),
            deviceId = deviceId,
            moduleId = ModuleId("meta"),
            data = MetaInfo(
                deviceLabel = label,
                deviceId = deviceId,
                octiVersionName = "0.14.0",
                octiGitSha = "abc1234",
                deviceManufacturer = "Google",
                deviceName = label,
                deviceType = type,
                deviceBootedAt = now.minusSeconds(86400),
                androidVersionName = "14",
                androidApiLevel = 34,
                androidSecurityPatch = "2024-01-05",
            ),
        ),
        moduleItems = listOf(
            DashboardVM.ModuleItem.Power(
                data = ModuleData(
                    modifiedAt = now.minusSeconds(60),
                    deviceId = deviceId,
                    moduleId = ModuleId("power"),
                    data = PowerInfo(
                        status = Status.DISCHARGING,
                        battery = PowerInfo.Battery(level = batteryLevel, scale = 100, health = 2, temp = 28f),
                        chargeIO = ChargeIO(null, null, null, null, null),
                    ),
                ),
                batteryLowAlert = null,
                showSettings = true,
            ),
        ),
        isCollapsed = isCollapsed,
        isLimited = false,
        isCurrentDevice = false,
    )

    DashboardScreen(
        state = DashboardVM.State(
            devices = listOf(
                previewDevice(deviceId1, "Pixel 8", MetaInfo.DeviceType.PHONE, 75, false),
                previewDevice(deviceId2, "Galaxy Tab S9", MetaInfo.DeviceType.TABLET, 42, true),
            ),
            deviceCount = 2,
            syncStatus = DashboardVM.SyncStatus.Idle(
                lastSyncAt = now.minusSeconds(300),
                connectorTypes = listOf(ConnectorType.GDRIVE, ConnectorType.OCTISERVER),
                syncDetail = DashboardVM.SyncDetail(modules = emptyList(), connectors = emptyList()),
                orchestratorState = previewOrchestratorState(),
                now = now,
            ),
            isOffline = false,
            showSyncSetup = false,
            missingPermissions = emptyList(),
            update = null,
            upgradeInfo = PreviewUpgradeInfo(),
            deviceLimitReached = false,
        ),
        onRefresh = {},
        onSyncServices = {},
        onDegradedClick = {},
        onConnectorDevices = {},
        onUpgrade = {},
        onSettings = {},
        onDismissSyncSetup = {},
        onSetupSync = {},
        onGrantPermission = {},
        onDismissPermission = {},
        onDismissUpdate = {},
        onViewUpdate = {},
        onStartUpdate = {},
        onToggleSyncExpanded = {},
        onToggleDeviceCollapsed = {},
        onPowerAlerts = {},
        onAppsList = {},
        onInstallLatestApp = {},
        onClearClipboard = {},
        onShareClipboard = {},
        onCopyClipboard = {},
        onWifiPermissionGrant = {},
    )
}

// endregion
