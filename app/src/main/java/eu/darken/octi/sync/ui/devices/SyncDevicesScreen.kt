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
import androidx.compose.runtime.remember
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
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.StalenessUtil
import java.time.Instant

@Composable
fun SyncDevicesScreenHost(
    connectorId: String,
    vm: SyncDevicesVM = hiltViewModel(),
) {
    vm.initialize(connectorId)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)
    state?.let {
        SyncDevicesScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDeleteDevice = { deviceId -> vm.deleteDevice(deviceId) },
        )
    }
}

@Composable
fun SyncDevicesScreen(
    state: SyncDevicesVM.State,
    onNavigateUp: () -> Unit,
    onDeleteDevice: (DeviceId) -> Unit,
) {
    var selectedDevice by remember { mutableStateOf<SyncDevicesVM.DeviceItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sync_synced_devices_label)) },
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
                    onClick = { selectedDevice = item },
                )
            }
        }
    }

    selectedDevice?.let { device ->
        DeviceActionsSheet(
            device = device,
            connectorType = state.connectorType,
            onDismiss = { selectedDevice = null },
            onDelete = {
                selectedDevice = null
                onDeleteDevice(device.deviceId)
            },
        )
    }
}

@Composable
private fun DeviceRow(
    item: SyncDevicesVM.DeviceItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(
            imageVector = when (item.metaInfo?.deviceType) {
                MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                else -> Icons.TwoTone.QuestionMark
            },
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.metaInfo?.labelOrFallback ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                item.metaInfo?.octiVersionName?.let { version ->
                    Text(
                        text = version,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Text(
                text = item.deviceId.id,
                style = MaterialTheme.typography.bodySmall,
            )

            item.lastSeen?.let { lastSeen ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DateUtils.getRelativeTimeSpanString(lastSeen.toEpochMilli()).toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val isStale = StalenessUtil.isStale(item.lastSeen)
            if (isStale && item.lastSeen != null) {
                val stalePeriod = StalenessUtil.formatStalePeriod(context, item.lastSeen)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(SyncR.string.sync_device_stale_warning_text, stalePeriod),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            item.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 10,
                )
            }
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
            connectorType = "kserver",
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
                        deviceBootedAt = Instant.now(),
                        androidVersionName = "14",
                        androidApiLevel = 34,
                        androidSecurityPatch = "2024-01-05",
                    ),
                    lastSeen = Instant.now(),
                    error = null,
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
                        deviceBootedAt = Instant.now(),
                        androidVersionName = "14",
                        androidApiLevel = 34,
                        androidSecurityPatch = "2024-01-01",
                    ),
                    lastSeen = Instant.now().minusSeconds(86400 * 60),
                    error = RuntimeException("Connection timed out"),
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
        state = SyncDevicesVM.State(connectorType = "kserver"),
        onNavigateUp = {},
        onDeleteDevice = {},
    )
}
