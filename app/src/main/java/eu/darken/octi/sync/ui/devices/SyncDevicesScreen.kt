package eu.darken.octi.sync.ui.devices

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceRemovalPolicy
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
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
    state?.let {
        SyncDevicesScreen(
            state = it,
            initialDeviceId = initialDeviceId,
            onNavigateUp = { vm.navUp() },
            onDeleteDevice = { deviceId -> vm.deleteDevice(deviceId) },
        )
    }
}

@Composable
fun SyncDevicesScreen(
    state: SyncDevicesVM.State,
    initialDeviceId: String? = null,
    onNavigateUp: () -> Unit,
    onDeleteDevice: (DeviceId) -> Unit,
) {
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialConsumed by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(SyncR.string.sync_synced_devices_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
        )
    }
}

@Composable
private fun DeviceRow(
    item: SyncDevicesVM.DeviceItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (item.metaInfo?.deviceType) {
                    MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                    MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                    else -> Icons.TwoTone.QuestionMark
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.metaInfo?.labelOrFallback ?: "",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                item.serverVersion?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                item.serverPlatform?.let { platform ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (platform.lowercase()) {
                                "android" -> Icons.TwoTone.Android
                                else -> Icons.TwoTone.QuestionMark
                            },
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = platform.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item.lastSeen?.let { lastSeen ->
            Text(
                text = stringResource(
                    SyncR.string.sync_device_last_seen_label,
                    DateUtils.getRelativeTimeSpanString(lastSeen.toEpochMilliseconds()).toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error.localizedMessage ?: stringResource(eu.darken.octi.common.R.string.general_error_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 10,
            )
        }

        item.issues.forEach { issue ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = issue.label.get(context),
                style = MaterialTheme.typography.bodySmall,
                color = when (issue.severity) {
                    IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                    IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                },
            )
        }
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
    )
}

@Preview2
@Composable
private fun SyncDevicesScreenEmptyPreview() = PreviewWrapper {
    SyncDevicesScreen(
        state = SyncDevicesVM.State(deviceRemovalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE),
        onNavigateUp = {},
        onDeleteDevice = {},
    )
}
