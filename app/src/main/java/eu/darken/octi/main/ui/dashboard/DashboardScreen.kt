package eu.darken.octi.main.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.CloudSync
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.NotificationsNone
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleId
import java.time.Instant
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.permissions.descriptionRes
import eu.darken.octi.common.permissions.labelRes
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.modules.apps.AppsModule
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.installerIconRes
import eu.darken.octi.modules.clipboard.ClipboardModule
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.ConnectivityModule
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.ui.icon
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.PowerModule
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter
import eu.darken.octi.modules.power.ui.batteryIcon
import eu.darken.octi.modules.wifi.WifiModule
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.receptIcon
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.syncs.gdrive.R as GDriveR
import eu.darken.octi.syncs.kserver.R as KServerR
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.StalenessUtil

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
            onUpgrade = { vm.goToUpgrade() },
            onSettings = { vm.goToSettings() },
            onDismissSyncSetup = { vm.dismissSyncSetup() },
            onSetupSync = { vm.setupSync() },
            onGrantPermission = { vm.requestPermission(it) },
            onDismissPermission = { vm.dismissPermission(it) },
            onDismissUpdate = { vm.dismissUpdate() },
            onViewUpdate = { vm.viewUpdate() },
            onStartUpdate = { vm.startUpdate() },
            onToggleDeviceCollapsed = { vm.toggleDeviceCollapsed(it) },
            onPowerAlerts = { vm.goToPowerAlerts(it) },
            onAppsList = { vm.goToAppsList(it) },
            onInstallLatestApp = { vm.onInstallLatestApp(it) },
            onClearClipboard = { vm.clearClipboard() },
            onShareClipboard = { vm.shareCurrentClipboard() },
            onCopyClipboard = { vm.setOsClipboard(it) },
            onWifiPermissionGrant = { vm.showPermissionPopup(it) },
        )
    }
}

@Composable
fun DashboardScreen(
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    state: DashboardVM.State,
    onRefresh: () -> Unit,
    onSyncServices: () -> Unit,
    onUpgrade: () -> Unit,
    onSettings: () -> Unit,
    onDismissSyncSetup: () -> Unit,
    onSetupSync: () -> Unit,
    onGrantPermission: (Permission) -> Unit,
    onDismissPermission: (Permission) -> Unit,
    onDismissUpdate: () -> Unit,
    onViewUpdate: () -> Unit,
    onStartUpdate: () -> Unit,
    onToggleDeviceCollapsed: (String) -> Unit,
    onPowerAlerts: (DeviceId) -> Unit,
    onAppsList: (DeviceId) -> Unit,
    onInstallLatestApp: (AppsInfo) -> Unit,
    onClearClipboard: () -> Unit,
    onShareClipboard: () -> Unit,
    onCopyClipboard: (ClipboardInfo) -> Unit,
    onWifiPermissionGrant: (Permission) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
    val offlineMessage = stringResource(CommonR.string.general_internal_not_available_msg)

    // Detail dialog state
    var showPowerDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Power?>(null) }
    var showWifiDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Wifi?>(null) }
    var showConnectivityDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Connectivity?>(null) }
    var showClipboardDetail by remember { mutableStateOf<DashboardVM.ModuleItem.Clipboard?>(null) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            // Sync status bar
            state.syncStatus?.let { syncStatus ->
                item(key = "sync_status") {
                    SyncStatusBar(
                        syncStatus = syncStatus,
                        onRefresh = onRefresh,
                    )
                }
            }

            // Sync setup card
                if (state.showSyncSetup) {
                    item(key = "sync_setup") {
                        SyncSetupCard(
                            onDismiss = onDismissSyncSetup,
                            onSetup = onSetupSync,
                        )
                    }
                }

                // Permission cards
                items(
                    items = state.missingPermissions,
                    key = { "perm_${it.permissionId}" },
                ) { permission ->
                    PermissionCard(
                        permission = permission,
                        onGrant = onGrantPermission,
                        onDismiss = onDismissPermission,
                    )
                }

                // Update card
                if (state.update != null) {
                    item(key = "update") {
                        UpdateCard(
                            update = state.update,
                            onDismiss = onDismissUpdate,
                            onViewUpdate = onViewUpdate,
                            onUpdate = onStartUpdate,
                        )
                    }
                }

                // Device cards with limit card interleaved
                val devices = state.devices
                val deviceLimit = 3
                val showLimitCard = state.deviceLimitReached

                if (showLimitCard) {
                    // Show devices before limit
                    items(
                        items = devices.take(deviceLimit),
                        key = { it.meta.deviceId.hashCode() },
                    ) { device ->
                        DashboardDeviceCard(
                            device = device,
                            onToggleCollapse = onToggleDeviceCollapsed,
                            onUpgrade = onUpgrade,
                            onManageStaleDevice = onSyncServices,
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
                        )
                    }

                    // Device limit card
                    item(key = "device_limit") {
                        DeviceLimitCard(
                            current = devices.size,
                            maximum = deviceLimit,
                            onManageDevices = onSyncServices,
                            onUpgrade = onUpgrade,
                        )
                    }

                    // Show devices after limit
                    items(
                        items = devices.drop(deviceLimit),
                        key = { it.meta.deviceId.hashCode() + 1000 },
                    ) { device ->
                        DashboardDeviceCard(
                            device = device,
                            onToggleCollapse = onToggleDeviceCollapsed,
                            onUpgrade = onUpgrade,
                            onManageStaleDevice = onSyncServices,
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
                        )
                    }
                } else {
                    items(
                        items = devices,
                        key = { it.meta.deviceId.hashCode() },
                    ) { device ->
                        DashboardDeviceCard(
                            device = device,
                            onToggleCollapse = onToggleDeviceCollapsed,
                            onUpgrade = onUpgrade,
                            onManageStaleDevice = onSyncServices,
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
                        )
                    }
                }

                // Upgrade card at bottom
                if (!state.upgradeInfo.isPro) {
                    item(key = "upgrade") {
                        UpgradeCard(
                            deviceLimit = state.deviceLimit,
                            onUpgrade = onUpgrade,
                        )
                    }
                }
            }
    }

    // Detail sheets
    showPowerDetail?.let { item ->
        PowerDetailSheet(
            power = item,
            onDismiss = { showPowerDetail = null },
        )
    }

    showWifiDetail?.let { item ->
        WifiDetailSheet(
            wifi = item,
            onDismiss = { showWifiDetail = null },
        )
    }

    showConnectivityDetail?.let { item ->
        ConnectivityDetailSheet(
            connectivity = item,
            onDismiss = { showConnectivityDetail = null },
            showMessage = showMessage,
        )
    }

    showClipboardDetail?.let { item ->
        ClipboardDetailSheet(
            clipboard = item,
            onDismiss = { showClipboardDetail = null },
            onCopy = onCopyClipboard,
            showMessage = showMessage,
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
private fun connectorTypesLabel(connectorTypes: List<String>): String? {
    if (connectorTypes.isEmpty()) return null
    val names = connectorTypes.map { type ->
        when (type) {
            "gdrive" -> stringResource(GDriveR.string.sync_gdrive_type_label)
            "kserver" -> stringResource(KServerR.string.sync_kserver_type_label)
            else -> type
        }
    }
    return stringResource(R.string.dashboard_sync_status_via, names.joinToString(", "))
}

@Composable
private fun SyncStatusBar(
    syncStatus: DashboardVM.SyncStatus,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.CloudSync,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            when (syncStatus) {
                is DashboardVM.SyncStatus.Syncing -> {
                    val moduleNames = syncStatus.syncingModules
                        .sortedBy { MODULE_DISPLAY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
                        .mapNotNull { moduleIdToStringRes(it) }
                        .map { stringResource(it) }
                    val text = if (moduleNames.isNotEmpty()) {
                        stringResource(R.string.dashboard_sync_status_syncing_modules, moduleNames.joinToString(", "))
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
                is DashboardVM.SyncStatus.Error -> syncStatus.message ?: connectorTypesLabel(syncStatus.connectorTypes)
                else -> connectorTypesLabel(syncStatus.connectorTypes)
            }
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
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
}

private val MODULE_DISPLAY_ORDER = listOf(
    PowerModule.MODULE_ID,
    ConnectivityModule.MODULE_ID,
    WifiModule.MODULE_ID,
    ClipboardModule.MODULE_ID,
    AppsModule.MODULE_ID,
    MetaModule.MODULE_ID,
)

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
    onUpgrade: () -> Unit,
    onManageStaleDevice: () -> Unit,
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
    val meta = device.meta.data
    val isStale = StalenessUtil.isStale(device.meta.modifiedAt)
    val hasModules = device.moduleItems.isNotEmpty()
    val shouldShowModules = !device.isLimited && hasModules && !device.isCollapsed

    val chevronRotation by animateFloatAsState(
        targetValue = if (device.isCollapsed) 0f else 90f,
        label = "chevronRotation",
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    when {
                        hasModules && !device.isLimited -> onToggleCollapse(device.meta.deviceId.id)
                        device.isLimited -> onUpgrade()
                    }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when {
                    device.isLimited -> Icons.TwoTone.Stars
                    device.isCurrentDevice -> Icons.TwoTone.Home
                    else -> when (meta.deviceType) {
                        MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                        MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                        MetaInfo.DeviceType.UNKNOWN -> Icons.TwoTone.QuestionMark
                    }
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta.deviceLabel?.let { "$it (${meta.deviceName})" } ?: meta.deviceName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val osName = stringResource(MetaR.string.module_meta_android_name_x_label, meta.androidVersionName)
                Text(
                    text = "$osName (API ${meta.androidApiLevel})",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    Text(
                        text = if (BuildConfigWrap.DEBUG) "Octi #${meta.octiGitSha}" else "Octi v${meta.octiVersionName}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = DateUtils.getRelativeTimeSpanString(device.meta.modifiedAt.toEpochMilli()).toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (!device.isLimited && hasModules) {
                Icon(
                    imageVector = Icons.TwoTone.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(chevronRotation),
                )
            }
        }

        // Module items
        if (shouldShowModules) {
            HorizontalDivider()

            // Stale warning
            if (isStale) {
                StaleDeviceWarning(
                    lastSyncTime = device.meta.modifiedAt,
                    onManageDevice = onManageStaleDevice,
                )
                HorizontalDivider()
            }

            device.moduleItems.forEachIndexed { index, moduleItem ->
                when (moduleItem) {
                    is DashboardVM.ModuleItem.Power -> PowerModuleItem(
                        item = moduleItem,
                        deviceId = device.meta.deviceId,
                        onDetailClicked = { onPowerClicked(moduleItem) },
                        onSettingsClicked = { onPowerAlerts(device.meta.deviceId) },
                    )

                    is DashboardVM.ModuleItem.Wifi -> WifiModuleItem(
                        item = moduleItem,
                        onDetailClicked = { onWifiClicked(moduleItem) },
                        onGrantPermission = onWifiPermissionGrant,
                    )

                    is DashboardVM.ModuleItem.Connectivity -> ConnectivityModuleItem(
                        item = moduleItem,
                        onDetailClicked = { onConnectivityClicked(moduleItem) },
                    )

                    is DashboardVM.ModuleItem.Apps -> AppsModuleItem(
                        item = moduleItem,
                        deviceId = device.meta.deviceId,
                        onAppsClicked = { onAppsClicked(device.meta.deviceId) },
                        onInstallClicked = { onInstallLatestApp(moduleItem.data.data) },
                    )

                    is DashboardVM.ModuleItem.Clipboard -> ClipboardModuleItem(
                        item = moduleItem,
                        onDetailClicked = { onClipboardClicked(moduleItem) },
                        onClearClicked = onClearClipboard,
                        onShareClicked = onShareClipboard,
                        onCopyClicked = { onCopyClipboard(moduleItem.data.data) },
                        showMessage = showMessage,
                    )
                }
                if (index < device.moduleItems.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StaleDeviceWarning(
    lastSyncTime: java.time.Instant,
    onManageDevice: () -> Unit,
) {
    val context = LocalContext.current
    val stalePeriod = StalenessUtil.formatStalePeriod(context, lastSyncTime)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(SyncR.string.sync_device_stale_warning_text, stalePeriod),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onManageDevice) {
            Text(stringResource(CommonR.string.general_manage_action))
        }
    }
}

// endregion

// region Module Items

@Composable
private fun PowerModuleItem(
    item: DashboardVM.ModuleItem.Power,
    deviceId: DeviceId,
    onDetailClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    val power = item.data.data
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = power.batteryIcon,
            contentDescription = null,
            tint = if (power.battery.percent < 0.1f && !power.isCharging) {
                colorResource(R.color.error)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val percentTxt = (power.battery.percent * 100).toInt()
            val stateTxt = powerStatusText(power)
            val lowAlert = item.batteryLowAlert
            val isAlertActive = lowAlert?.triggeredAt != null && lowAlert.dismissedAt == null
            Text(
                text = "$percentTxt% - $stateTxt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isAlertActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isAlertActive) colorResource(R.color.error) else MaterialTheme.colorScheme.onSurface,
            )
            PowerSecondaryText(power)
        }
        if (item.batteryLowAlert != null) {
            Icon(
                imageVector = Icons.TwoTone.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        if (item.showSettings) {
            IconButton(onClick = onSettingsClicked, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.TwoTone.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun powerStatusText(power: PowerInfo): String = when (power.status) {
    Status.FULL -> stringResource(PowerR.string.module_power_battery_status_full)
    Status.CHARGING -> when (power.chargeIO.speed) {
        ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_charging_slow)
        ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_charging_fast)
        else -> stringResource(PowerR.string.module_power_battery_status_charging)
    }

    Status.DISCHARGING -> when (power.chargeIO.speed) {
        ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_discharging_slow)
        ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_discharging_fast)
        else -> stringResource(PowerR.string.module_power_battery_status_discharging)
    }

    else -> stringResource(PowerR.string.module_power_battery_status_unknown)
}

@Composable
private fun PowerSecondaryText(power: PowerInfo) {
    val context = LocalContext.current
    val estimationFormatter = remember { PowerEstimationFormatter(context) }
    val temperatureFormatter = remember { TemperatureFormatter(context) }
    val estimationText = estimationFormatter.format(power)
    val temperatureText = power.battery.temp?.let { temperatureFormatter.formatTemperature(it) }
    Text(
        text = if (temperatureText != null) "$temperatureText - $estimationText" else estimationText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun WifiModuleItem(
    item: DashboardVM.ModuleItem.Wifi,
    onDetailClicked: () -> Unit,
    onGrantPermission: (Permission) -> Unit,
) {
    val wifi = item.data.data
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = wifi.receptIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val freqText = when (wifi.currentWifi?.freqType) {
                WifiInfo.Wifi.Type.FIVE_GHZ -> "5 Ghz"
                WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> "2.4 Ghz"
                else -> stringResource(CommonR.string.general_na_label)
            }
            val sig = wifi.currentWifi?.reception ?: 0f
            val reception = when {
                sig > 0.65f -> stringResource(WifiR.string.module_wifi_reception_good_label)
                sig > 0.3f -> stringResource(WifiR.string.module_wifi_reception_okay_label)
                sig > 0.0f -> stringResource(WifiR.string.module_wifi_reception_bad_label)
                else -> stringResource(CommonR.string.general_na_label)
            }
            Text(
                text = "$freqText - $reception",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = wifi.currentWifi?.ssid ?: stringResource(WifiR.string.module_wifi_unknown_ssid_label),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (item.showPermissionAction) {
            IconButton(
                onClick = { onGrantPermission(Permission.ACCESS_FINE_LOCATION) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectivityModuleItem(
    item: DashboardVM.ModuleItem.Connectivity,
    onDetailClicked: () -> Unit,
) {
    val connectivity = item.data.data
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = connectivity.connectionType.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val typeLabel = when (connectivity.connectionType) {
                ConnectivityInfo.ConnectionType.WIFI -> stringResource(ConnectivityR.string.module_connectivity_type_wifi_label)
                ConnectivityInfo.ConnectionType.CELLULAR -> stringResource(ConnectivityR.string.module_connectivity_type_cellular_label)
                ConnectivityInfo.ConnectionType.ETHERNET -> stringResource(ConnectivityR.string.module_connectivity_type_ethernet_label)
                ConnectivityInfo.ConnectionType.NONE, null -> stringResource(ConnectivityR.string.module_connectivity_type_none_label)
            }
            Text(
                text = "${stringResource(ConnectivityR.string.module_connectivity_detail_connection_type_label)}: $typeLabel",
                style = MaterialTheme.typography.bodyMedium,
            )
            val localIp = connectivity.localAddressIpv4 ?: stringResource(ConnectivityR.string.module_connectivity_unknown_local_ip_label)
            val publicIp = connectivity.publicIp ?: stringResource(ConnectivityR.string.module_connectivity_unknown_public_ip_label)
            Text(
                text = "$localIp - $publicIp",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AppsModuleItem(
    item: DashboardVM.ModuleItem.Apps,
    deviceId: DeviceId,
    onAppsClicked: () -> Unit,
    onInstallClicked: () -> Unit,
) {
    val apps = item.data.data
    val last = apps.installedPackages.maxByOrNull { it.installedAt }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAppsClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Apps,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(
                    AppsR.plurals.module_apps_x_installed,
                    apps.installedPackages.size,
                    apps.installedPackages.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (last != null) {
                Text(
                    text = stringResource(
                        AppsR.string.module_apps_last_installed_x,
                        "${last.label} (${last.versionName})",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (last != null) {
            IconButton(
                onClick = onInstallClicked,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(last.installerIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ClipboardModuleItem(
    item: DashboardVM.ModuleItem.Clipboard,
    onDetailClicked: () -> Unit,
    onClearClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onCopyClicked: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val clip = item.data.data
    val copiedToOctiMsg = stringResource(ClipboardR.string.module_clipboard_copied_os_to_octi)
    val copiedToOsMsg = stringResource(ClipboardR.string.module_clipboard_copied_octi_to_os)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.ContentPaste,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val clipText = when (clip.type) {
                ClipboardInfo.Type.EMPTY -> stringResource(CommonR.string.general_empty_label)
                ClipboardInfo.Type.SIMPLE_TEXT -> clip.data.utf8()
            }
            Text(
                text = "\"$clipText\"",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isOurDevice) {
            IconButton(
                onClick = {
                    onShareClicked()
                    showMessage(copiedToOctiMsg)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        IconButton(
            onClick = {
                onCopyClicked()
                showMessage(copiedToOsMsg)
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.TwoTone.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// endregion

// region Detail Dialogs

@Composable
private fun PowerDetailSheet(
    power: DashboardVM.ModuleItem.Power,
    onDismiss: () -> Unit,
) {
    val powerInfo = power.data.data
    val context = LocalContext.current
    val temperatureFormatter = remember { TemperatureFormatter(context) }
    val estimationFormatter = remember { PowerEstimationFormatter(context) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(PowerR.string.module_power_label),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(
                label = stringResource(PowerR.string.module_power_detail_level_label),
                value = "${(powerInfo.battery.percent * 100).toInt()}%",
            )
            DetailRow(
                label = stringResource(PowerR.string.module_power_detail_status_label),
                value = when (powerInfo.status) {
                    Status.FULL -> stringResource(PowerR.string.module_power_battery_status_full)
                    Status.CHARGING -> stringResource(PowerR.string.module_power_battery_status_charging)
                    Status.DISCHARGING -> stringResource(PowerR.string.module_power_battery_status_discharging)
                    Status.UNKNOWN -> stringResource(PowerR.string.module_power_battery_status_unknown)
                },
            )
            DetailRow(
                label = stringResource(PowerR.string.module_power_detail_speed_label),
                value = when (powerInfo.status) {
                    Status.CHARGING -> when (powerInfo.chargeIO.speed) {
                        ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_charging_slow)
                        ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_charging_fast)
                        ChargeIO.Speed.NORMAL -> stringResource(PowerR.string.module_power_battery_status_charging)
                    }

                    Status.DISCHARGING -> when (powerInfo.chargeIO.speed) {
                        ChargeIO.Speed.SLOW -> stringResource(PowerR.string.module_power_battery_status_discharging_slow)
                        ChargeIO.Speed.FAST -> stringResource(PowerR.string.module_power_battery_status_discharging_fast)
                        ChargeIO.Speed.NORMAL -> stringResource(PowerR.string.module_power_battery_status_discharging)
                    }

                    else -> stringResource(CommonR.string.general_na_label)
                },
            )
            DetailRow(
                label = stringResource(PowerR.string.module_power_detail_temperature_label),
                value = powerInfo.battery.temp?.let { temperatureFormatter.formatTemperature(it) }
                    ?: stringResource(CommonR.string.general_na_label),
            )
            DetailRow(
                label = stringResource(PowerR.string.module_power_detail_estimation_label),
                value = estimationFormatter.format(powerInfo),
            )
            DetailRow(
                label = stringResource(PowerR.string.module_power_detail_health_label),
                value = when (powerInfo.battery.health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> stringResource(PowerR.string.module_power_detail_health_good)
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> stringResource(PowerR.string.module_power_detail_health_overheat)
                    BatteryManager.BATTERY_HEALTH_DEAD -> stringResource(PowerR.string.module_power_detail_health_dead)
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> stringResource(PowerR.string.module_power_detail_health_over_voltage)
                    BatteryManager.BATTERY_HEALTH_COLD -> stringResource(PowerR.string.module_power_detail_health_cold)
                    else -> stringResource(PowerR.string.module_power_detail_health_unknown)
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WifiDetailSheet(
    wifi: DashboardVM.ModuleItem.Wifi,
    onDismiss: () -> Unit,
) {
    val wifiInfo = wifi.data.data
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(WifiR.string.module_wifi_label),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(
                label = stringResource(WifiR.string.module_wifi_detail_ssid_label),
                value = wifiInfo.currentWifi?.ssid
                    ?: stringResource(WifiR.string.module_wifi_unknown_ssid_label),
            )
            DetailRow(
                label = stringResource(WifiR.string.module_wifi_detail_frequency_label),
                value = when (wifiInfo.currentWifi?.freqType) {
                    WifiInfo.Wifi.Type.FIVE_GHZ -> stringResource(WifiR.string.module_wifi_freq_5ghz)
                    WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ -> stringResource(WifiR.string.module_wifi_freq_2_4ghz)
                    else -> stringResource(CommonR.string.general_na_label)
                },
            )
            val sig = wifiInfo.currentWifi?.reception ?: 0f
            DetailRow(
                label = stringResource(WifiR.string.module_wifi_detail_signal_label),
                value = when {
                    sig > 0.65f -> stringResource(WifiR.string.module_wifi_reception_good_label)
                    sig > 0.3f -> stringResource(WifiR.string.module_wifi_reception_okay_label)
                    sig > 0.0f -> stringResource(WifiR.string.module_wifi_reception_bad_label)
                    else -> stringResource(CommonR.string.general_na_label)
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectivityDetailSheet(
    connectivity: DashboardVM.ModuleItem.Connectivity,
    onDismiss: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val info = connectivity.data.data
    val unknownLocal = stringResource(ConnectivityR.string.module_connectivity_unknown_local_ip_label)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(ConnectivityR.string.module_connectivity_label),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(
                label = stringResource(ConnectivityR.string.module_connectivity_detail_connection_type_label),
                value = when (info.connectionType) {
                    ConnectivityInfo.ConnectionType.WIFI -> stringResource(ConnectivityR.string.module_connectivity_type_wifi_label)
                    ConnectivityInfo.ConnectionType.CELLULAR -> stringResource(ConnectivityR.string.module_connectivity_type_cellular_label)
                    ConnectivityInfo.ConnectionType.ETHERNET -> stringResource(ConnectivityR.string.module_connectivity_type_ethernet_label)
                    ConnectivityInfo.ConnectionType.NONE, null -> stringResource(ConnectivityR.string.module_connectivity_type_none_label)
                },
            )
            CopyableDetailRow(
                label = stringResource(ConnectivityR.string.module_connectivity_detail_public_ip_label),
                value = info.publicIp ?: stringResource(ConnectivityR.string.module_connectivity_unknown_public_ip_label),
                copyable = info.publicIp != null,
                showMessage = showMessage,
            )
            CopyableDetailRow(
                label = stringResource(ConnectivityR.string.module_connectivity_detail_local_ipv4_label),
                value = info.localAddressIpv4 ?: unknownLocal,
                copyable = info.localAddressIpv4 != null,
                showMessage = showMessage,
            )
            CopyableDetailRow(
                label = stringResource(ConnectivityR.string.module_connectivity_detail_local_ipv6_label),
                value = info.localAddressIpv6 ?: unknownLocal,
                copyable = info.localAddressIpv6 != null,
                showMessage = showMessage,
            )
            DetailRow(
                label = stringResource(ConnectivityR.string.module_connectivity_detail_gateway_label),
                value = info.gatewayIp ?: unknownLocal,
            )
            DetailRow(
                label = stringResource(ConnectivityR.string.module_connectivity_detail_dns_label),
                value = info.dnsServers?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: unknownLocal,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClipboardDetailSheet(
    clipboard: DashboardVM.ModuleItem.Clipboard,
    onDismiss: () -> Unit,
    onCopy: (ClipboardInfo) -> Unit,
    showMessage: (String) -> Unit,
) {
    val clip = clipboard.data.data
    val copiedMsg = stringResource(ClipboardR.string.module_clipboard_copied_octi_to_os)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(ClipboardR.string.module_clipboard_label),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(
                label = stringResource(ClipboardR.string.module_clipboard_detail_type_label),
                value = when (clip.type) {
                    ClipboardInfo.Type.EMPTY -> stringResource(ClipboardR.string.module_clipboard_detail_type_empty)
                    ClipboardInfo.Type.SIMPLE_TEXT -> stringResource(ClipboardR.string.module_clipboard_detail_type_text)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(ClipboardR.string.module_clipboard_detail_content_label),
                style = MaterialTheme.typography.labelMedium,
            )
            val content = when (clip.type) {
                ClipboardInfo.Type.EMPTY -> ""
                ClipboardInfo.Type.SIMPLE_TEXT -> clip.data.utf8()
            }
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = {
                    onCopy(clip)
                    showMessage(copiedMsg)
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(ClipboardR.string.module_clipboard_copy_action))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CopyableDetailRow(label: String, value: String, copyable: Boolean, showMessage: (String) -> Unit) {
    val context = LocalContext.current
    val copiedMsg = stringResource(CommonR.string.general_copy_action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
        )
        if (copyable) {
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("IP", value))
                    showMessage(copiedMsg)
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.ContentCopy,
                    contentDescription = stringResource(CommonR.string.general_copy_action),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// endregion

// region Previews

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
        onUpgrade = {},
        onSettings = {},
        onDismissSyncSetup = {},
        onSetupSync = {},
        onGrantPermission = {},
        onDismissPermission = {},
        onDismissUpdate = {},
        onViewUpdate = {},
        onStartUpdate = {},
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
                connectorTypes = listOf("gdrive"),
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
        onUpgrade = {},
        onSettings = {},
        onDismissSyncSetup = {},
        onSetupSync = {},
        onGrantPermission = {},
        onDismissPermission = {},
        onDismissUpdate = {},
        onViewUpdate = {},
        onStartUpdate = {},
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
                connectorTypes = listOf("gdrive", "kserver"),
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
        onUpgrade = {},
        onSettings = {},
        onDismissSyncSetup = {},
        onSetupSync = {},
        onGrantPermission = {},
        onDismissPermission = {},
        onDismissUpdate = {},
        onViewUpdate = {},
        onStartUpdate = {},
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
