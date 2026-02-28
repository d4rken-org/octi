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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.StalenessUtil

@Composable
fun SyncDevicesScreenHost(
    connectorId: String,
    vm: SyncDevicesVM = hiltViewModel(),
) {
    vm.initialize(connectorId)

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by waitForState(vm.state)
    state?.let {
        SyncDevicesScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onDeleteDevice = { deviceId -> vm.deleteDevice(deviceId) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        DeviceActionsDialog(
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
            painter = painterResource(
                when (item.metaInfo?.deviceType) {
                    MetaInfo.DeviceType.PHONE -> R.drawable.ic_baseline_phone_android_24
                    MetaInfo.DeviceType.TABLET -> R.drawable.ic_baseline_tablet_android_24
                    else -> R.drawable.ic_baseline_question_mark_24
                }
            ),
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

@Composable
private fun DeviceActionsDialog(
    device: SyncDevicesVM.DeviceItem,
    connectorType: String?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val removeIsRevoke = when (connectorType) {
        "gdrive" -> false
        "kserver" -> true
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = device.metaInfo?.labelOrFallback ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = device.deviceId.id,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        text = {
            Column {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_delete_sweep_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(CommonR.string.general_remove_action))
                }

                removeIsRevoke?.let { isRevoke ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (isRevoke) {
                            true -> stringResource(R.string.sync_delete_device_revokeaccess_caveat)
                            false -> stringResource(R.string.sync_delete_device_keepaccess_caveat)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CommonR.string.general_cancel_action))
            }
        },
    )
}
