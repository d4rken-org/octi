package eu.darken.octi.main.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.TemperatureFormatter
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.permissions.Permission
import eu.darken.octi.common.permissions.descriptionRes
import eu.darken.octi.common.permissions.labelRes
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.apps.core.AppsInfo
import eu.darken.octi.modules.apps.core.installerIconRes
import eu.darken.octi.modules.clipboard.ClipboardInfo
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.connectivity.core.ConnectivityInfo
import eu.darken.octi.modules.connectivity.ui.iconRes
import eu.darken.octi.modules.meta.R as MetaR
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.ChargeIO
import eu.darken.octi.modules.power.core.PowerInfo.Status
import eu.darken.octi.modules.power.ui.PowerEstimationFormatter
import eu.darken.octi.modules.power.ui.batteryIconRes
import eu.darken.octi.modules.wifi.R as WifiR
import eu.darken.octi.modules.wifi.core.WifiInfo
import eu.darken.octi.modules.wifi.ui.receptIconRes
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.StalenessUtil

@Composable
fun DashboardScreenHost(vm: DashboardVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current

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
                    Toast.makeText(
                        context,
                        context.getString(R.string.permission_dismiss_hint),
                        Toast.LENGTH_SHORT,
                    ).show()
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

    val state by waitForState(vm.state)
    state?.let {
        DashboardScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
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
    val snackbarHostState = remember { SnackbarHostState() }
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
                            painter = painterResource(R.drawable.ic_baseline_cloud_sync_24),
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
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
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
                        )
                    }
                }

                // Upgrade card at bottom
                if (!state.upgradeInfo.isPro) {
                    item(key = "upgrade") {
                        UpgradeCard(onUpgrade = onUpgrade)
                    }
                }
            }
        }
    }

    // Detail dialogs
    showPowerDetail?.let { item ->
        PowerDetailDialog(
            power = item,
            onDismiss = { showPowerDetail = null },
        )
    }

    showWifiDetail?.let { item ->
        WifiDetailDialog(
            wifi = item,
            onDismiss = { showWifiDetail = null },
        )
    }

    showConnectivityDetail?.let { item ->
        ConnectivityDetailDialog(
            connectivity = item,
            onDismiss = { showConnectivityDetail = null },
        )
    }

    showClipboardDetail?.let { item ->
        ClipboardDetailDialog(
            clipboard = item,
            onDismiss = { showClipboardDetail = null },
            onCopy = onCopyClipboard,
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
    val lastSyncAt = state.lastSyncAt?.let {
        DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString()
    }
    val deviceInfo = if (lastSyncAt != null) "$deviceQuantity ($lastSyncAt)" else deviceQuantity
    return if (BuildConfigWrap.DEBUG) {
        "$deviceInfo ${BuildConfigWrap.GIT_SHA}"
    } else {
        deviceInfo
    }
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
private fun UpgradeCard(onUpgrade: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onUpgrade),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(CommonR.string.general_upgrade_action),
                style = MaterialTheme.typography.titleMedium,
            )
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
                painter = painterResource(
                    when {
                        device.isLimited -> R.drawable.ic_baseline_stars_24
                        device.isCurrentDevice -> R.drawable.ic_baseline_home_24
                        else -> when (meta.deviceType) {
                            MetaInfo.DeviceType.PHONE -> R.drawable.ic_baseline_phone_android_24
                            MetaInfo.DeviceType.TABLET -> R.drawable.ic_baseline_tablet_android_24
                            MetaInfo.DeviceType.UNKNOWN -> R.drawable.ic_baseline_question_mark_24
                        }
                    }
                ),
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
            painter = painterResource(power.batteryIconRes),
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
                painter = painterResource(PowerR.drawable.ic_bell_outline_24),
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
            painter = painterResource(wifi.receptIconRes),
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
            painter = painterResource(connectivity.connectionType.iconRes),
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
            painter = painterResource(R.drawable.ic_baseline_apps_24),
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
) {
    val clip = item.data.data
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDetailClicked)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_content_paste_24),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            val clipText = when (clip.type) {
                ClipboardInfo.Type.EMPTY -> stringResource(CommonR.string.general_empty_label)
                ClipboardInfo.Type.SIMPLE_TEXT -> clip.data.utf8()
                else -> clip.data.toString()
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
                    Toast.makeText(context, ClipboardR.string.module_clipboard_copied_os_to_octi, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, ClipboardR.string.module_clipboard_copied_octi_to_os, Toast.LENGTH_SHORT).show()
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
private fun PowerDetailDialog(
    power: DashboardVM.ModuleItem.Power,
    onDismiss: () -> Unit,
) {
    val powerInfo = power.data.data
    val context = LocalContext.current
    val temperatureFormatter = remember { TemperatureFormatter(context) }
    val estimationFormatter = remember { PowerEstimationFormatter(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(PowerR.string.module_power_label)) },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun WifiDetailDialog(
    wifi: DashboardVM.ModuleItem.Wifi,
    onDismiss: () -> Unit,
) {
    val wifiInfo = wifi.data.data
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(WifiR.string.module_wifi_label)) },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ConnectivityDetailDialog(
    connectivity: DashboardVM.ModuleItem.Connectivity,
    onDismiss: () -> Unit,
) {
    val info = connectivity.data.data
    val context = LocalContext.current
    val unknownLocal = stringResource(ConnectivityR.string.module_connectivity_unknown_local_ip_label)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ConnectivityR.string.module_connectivity_label)) },
        text = {
            Column {
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
                )
                CopyableDetailRow(
                    label = stringResource(ConnectivityR.string.module_connectivity_detail_local_ipv4_label),
                    value = info.localAddressIpv4 ?: unknownLocal,
                    copyable = info.localAddressIpv4 != null,
                )
                CopyableDetailRow(
                    label = stringResource(ConnectivityR.string.module_connectivity_detail_local_ipv6_label),
                    value = info.localAddressIpv6 ?: unknownLocal,
                    copyable = info.localAddressIpv6 != null,
                )
                DetailRow(
                    label = stringResource(ConnectivityR.string.module_connectivity_detail_gateway_label),
                    value = info.gatewayIp ?: unknownLocal,
                )
                DetailRow(
                    label = stringResource(ConnectivityR.string.module_connectivity_detail_dns_label),
                    value = info.dnsServers?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: unknownLocal,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ClipboardDetailDialog(
    clipboard: DashboardVM.ModuleItem.Clipboard,
    onDismiss: () -> Unit,
    onCopy: (ClipboardInfo) -> Unit,
) {
    val clip = clipboard.data.data
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ClipboardR.string.module_clipboard_label)) },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCopy(clip)
                Toast.makeText(context, ClipboardR.string.module_clipboard_copied_octi_to_os, Toast.LENGTH_SHORT).show()
            }) {
                Text(stringResource(ClipboardR.string.module_clipboard_copy_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
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
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CopyableDetailRow(label: String, value: String, copyable: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (copyable) {
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("IP", value))
                    Toast.makeText(
                        context,
                        context.getString(CommonR.string.general_copy_action),
                        Toast.LENGTH_SHORT,
                    ).show()
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
