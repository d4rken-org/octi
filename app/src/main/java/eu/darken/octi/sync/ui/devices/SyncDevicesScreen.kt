package eu.darken.octi.sync.ui.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.compose.SortModeDialog
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceRemovalPolicy
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.LocalConnectorContributions
import eu.darken.octi.sync.core.SyncDevicesSortMode
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Composable
fun SyncDevicesScreenHost(
    connectorId: String,
    initialDeviceId: String? = null,
    vm: SyncDevicesVM = hiltViewModel(),
) {
    vm.initialize(connectorId)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    var showSortDialog by rememberSaveable { mutableStateOf(false) }

    state?.let { current ->
        SyncDevicesScreen(
            state = current,
            initialDeviceId = initialDeviceId,
            onNavigateUp = { vm.navUp() },
            onDeleteDevice = { deviceId -> vm.deleteDevice(deviceId) },
            onSort = { showSortDialog = true },
            onRefresh = { vm.refresh() },
        )

        if (showSortDialog) {
            SortModeDialog(
                title = stringResource(SyncR.string.sync_devices_sort_label),
                currentMode = current.sortMode,
                modes = SyncDevicesSortMode.entries,
                onSelect = { mode ->
                    vm.updateSortMode(mode)
                    showSortDialog = false
                },
                onDismiss = { showSortDialog = false },
                reversed = current.sortReversed,
                onReverseChange = { vm.updateSortReversed(it) },
            )
        }
    }
}

@Composable
fun SyncDevicesScreen(
    state: SyncDevicesVM.State,
    initialDeviceId: String? = null,
    onNavigateUp: () -> Unit,
    onDeleteDevice: (DeviceId) -> Unit,
    onSort: () -> Unit,
    onRefresh: () -> Unit,
) {
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialConsumed by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }

    LaunchedEffect(initialDeviceId, state.items) {
        if (!initialConsumed && initialDeviceId != null && state.items.isNotEmpty()) {
            initialConsumed = true
            if (state.items.any { it.deviceId.id == initialDeviceId }) {
                selectedDeviceId = initialDeviceId
            }
        }
    }

    // Pop back if the connector becomes paused and nothing is in flight — paused connectors
    // shouldn't have an interactive devices screen. Guarded on deletingDeviceIds so an in-flight
    // pause-error dialog gets a chance to render before we navigate away.
    LaunchedEffect(state.isPaused, state.deletingDeviceIds) {
        if (state.isPaused && state.deletingDeviceIds.isEmpty()) onNavigateUp()
    }

    val selectedDevice = selectedDeviceId?.let { id -> state.items.find { it.deviceId.id == id } }

    val contribution = state.connectorType?.let { LocalConnectorContributions.current[it] }
    val subtitleText = contribution?.let { c ->
        val typeLabel = stringResource(c.labelRes)
        val account = state.accountLabel?.takeUnless { it.isBlank() }
        if (account != null) "$typeLabel · $account" else typeLabel
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(SyncR.string.sync_synced_devices_label))
                        if (subtitleText != null) {
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!state.isPaused) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                IconButton(onClick = onRefresh) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Refresh,
                                        contentDescription = stringResource(CommonR.string.general_sync_action),
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onSort) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.Sort,
                            contentDescription = stringResource(SyncR.string.sync_devices_sort_label),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(state.items, key = { it.deviceId.id }) { item ->
                DeviceRow(
                    item = item,
                    onClick = { selectedDeviceId = item.deviceId.id },
                )
            }
        }
    }

    selectedDevice?.let { device ->
        DeviceActionsSheet(
            device = device,
            removalPolicy = state.deviceRemovalPolicy,
            isPaused = state.isPaused,
            isDeleting = state.deletingDeviceIds.contains(device.deviceId),
            onDismiss = { selectedDeviceId = null },
            // Do NOT null selectedDeviceId here — once the VM's optimistic prune drops the
            // device from state.items, selectedDevice resolves to null and the sheet unmounts.
            onDelete = { onDeleteDevice(device.deviceId) },
            showMessage = showMessage,
        )
    }
}

@Preview2
@Composable
private fun SyncDevicesScreenPreview() = PreviewWrapper {
    val deviceId1 = DeviceId("device-abc-123")
    val deviceId2 = DeviceId("device-def-456")
    SyncDevicesScreen(
        state = SyncDevicesVM.State(
            deviceRemovalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
            items = listOf(
                SyncDevicesVM.DeviceItem(
                    deviceId = deviceId1,
                    metaInfo = MetaInfo(
                        deviceLabel = "Pixel 8",
                        deviceId = deviceId1,
                        octiVersionName = "0.14.0",
                        octiGitSha = "abc1234",
                        deviceManufacturer = "Google",
                        deviceName = "Pixel 8",
                        deviceType = MetaInfo.DeviceType.PHONE,
                        deviceBootedAt = Clock.System.now(),
                        androidVersionName = "14",
                        androidApiLevel = 34,
                        androidSecurityPatch = "2024-01-05",
                    ),
                    lastSeen = Clock.System.now(),
                    error = null,
                    serverVersion = "0.14.0",
                    serverAddedAt = Clock.System.now(),
                    serverPlatform = "android",
                ),
                SyncDevicesVM.DeviceItem(
                    deviceId = deviceId2,
                    metaInfo = MetaInfo(
                        deviceLabel = "Galaxy Tab S9",
                        deviceId = deviceId2,
                        octiVersionName = "0.13.0",
                        octiGitSha = "def5678",
                        deviceManufacturer = "Samsung",
                        deviceName = "Galaxy Tab S9",
                        deviceType = MetaInfo.DeviceType.TABLET,
                        deviceBootedAt = Clock.System.now(),
                        androidVersionName = "14",
                        androidApiLevel = 34,
                        androidSecurityPatch = "2024-01-01",
                    ),
                    lastSeen = Clock.System.now() - (86400 * 60).seconds,
                    error = RuntimeException("Connection timed out"),
                    serverVersion = "0.13.0",
                    serverAddedAt = Clock.System.now() - (86400 * 60).seconds,
                    serverPlatform = "android",
                ),
            ),
        ),
        onNavigateUp = {},
        onDeleteDevice = {},
        onSort = {},
        onRefresh = {},
    )
}

@Preview2
@Composable
private fun SyncDevicesScreenEmptyPreview() = PreviewWrapper {
    SyncDevicesScreen(
        state = SyncDevicesVM.State(deviceRemovalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE),
        onNavigateUp = {},
        onDeleteDevice = {},
        onSort = {},
        onRefresh = {},
    )
}

