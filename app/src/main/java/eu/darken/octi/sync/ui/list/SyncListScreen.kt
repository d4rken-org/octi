package eu.darken.octi.sync.ui.list

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.syncs.gdrive.R as GDriveR
import eu.darken.octi.syncs.kserver.R as KServerR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import androidx.compose.runtime.collectAsState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import eu.darken.octi.syncs.kserver.core.KServer
import eu.darken.octi.syncs.kserver.core.KServerConnector
import okio.ByteString.Companion.encodeUtf8
import java.time.Instant

@Composable
fun SyncListScreenHost(vm: SyncListVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        SyncListScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onAddConnector = { vm.addConnector() },
            onTogglePause = { id -> vm.togglePause(id) },
            onForceSync = { id -> vm.forceSync(id) },
            onViewDevices = { id -> vm.viewDevices(id) },
            onLinkNewDevice = { id -> vm.linkNewDevice(id) },
            onReset = { id -> vm.resetData(id) },
            onDisconnect = { id -> vm.disconnect(id) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncListScreen(
    state: SyncListVM.State,
    onNavigateUp: () -> Unit,
    onAddConnector: () -> Unit,
    onTogglePause: (ConnectorId) -> Unit,
    onForceSync: (ConnectorId) -> Unit,
    onViewDevices: (ConnectorId) -> Unit,
    onLinkNewDevice: (ConnectorId) -> Unit,
    onReset: (ConnectorId) -> Unit,
    onDisconnect: (ConnectorId) -> Unit,
) {
    var showActionsFor by remember { mutableStateOf<SyncListVM.ConnectorItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.sync_services_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddConnector) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.sync_add_label),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(
                items = state.connectors,
                key = { item ->
                    when (item) {
                        is SyncListVM.ConnectorItem.GDrive -> "gdrive-${item.account.id.id}"
                        is SyncListVM.ConnectorItem.KServer -> "kserver-${item.credentials.accountId.id}"
                    }
                },
            ) { item ->
                val isHighlighted = state.highlightedConnectorIds.contains(item.connectorId)
                when (item) {
                    is SyncListVM.ConnectorItem.GDrive -> GDriveConnectorCard(
                        item = item,
                        isHighlighted = isHighlighted,
                        onClick = { showActionsFor = item },
                    )

                    is SyncListVM.ConnectorItem.KServer -> KServerConnectorCard(
                        item = item,
                        isHighlighted = isHighlighted,
                        onClick = { showActionsFor = item },
                    )
                }
            }
        }
    }

    showActionsFor?.let { item ->
        when (item) {
            is SyncListVM.ConnectorItem.GDrive -> GDriveActionsDialog(
                item = item,
                onDismiss = { showActionsFor = null },
                onTogglePause = {
                    onTogglePause(item.connectorId)
                },
                onForceSync = {
                    onForceSync(item.connectorId)
                    showActionsFor = null
                },
                onViewDevices = {
                    onViewDevices(item.connectorId)
                    showActionsFor = null
                },
                onReset = {
                    onReset(item.connectorId)
                    showActionsFor = null
                },
                onDisconnect = {
                    onDisconnect(item.connectorId)
                    showActionsFor = null
                },
            )

            is SyncListVM.ConnectorItem.KServer -> KServerActionsDialog(
                item = item,
                onDismiss = { showActionsFor = null },
                onTogglePause = {
                    onTogglePause(item.connectorId)
                },
                onForceSync = {
                    onForceSync(item.connectorId)
                    showActionsFor = null
                },
                onViewDevices = {
                    onViewDevices(item.connectorId)
                    showActionsFor = null
                },
                onLinkNewDevice = {
                    onLinkNewDevice(item.connectorId)
                    showActionsFor = null
                },
                onReset = {
                    onReset(item.connectorId)
                    showActionsFor = null
                },
                onDisconnect = {
                    onDisconnect(item.connectorId)
                    showActionsFor = null
                },
            )
        }
    }
}

@Composable
private fun GDriveConnectorCard(
    item: SyncListVM.ConnectorItem.GDrive,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 500),
        label = "highlightBorder",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        border = if (isHighlighted) BorderStroke(2.dp, borderColor) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_gdrive_24),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildString {
                        append(stringResource(GDriveR.string.sync_gdrive_type_label))
                        if (item.account.isAppDataScope) {
                            append(" (${stringResource(GDriveR.string.sync_gdrive_appdata_label)})")
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ConnectorStatusIndicators(isBusy = item.ourState.isBusy, isPaused = item.isPaused)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LabeledField(
                label = stringResource(R.string.sync_account_label),
                value = item.account.email,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LastSyncField(state = item.ourState)

            Spacer(modifier = Modifier.height(8.dp))

            LabeledField(
                label = stringResource(CommonR.string.general_quota_label),
                value = item.ourState.quota
                    ?.let { stats ->
                        val total = Formatter.formatShortFileSize(context, stats.storageTotal)
                        val used = Formatter.formatShortFileSize(context, stats.storageUsed)
                        val free = Formatter.formatShortFileSize(context, stats.storageFree)
                        stringResource(R.string.sync_quota_storage_msg, free, used, total)
                    }
                    ?: stringResource(CommonR.string.general_na_label),
            )

            Spacer(modifier = Modifier.height(8.dp))

            DevicesField(state = item.ourState, otherStates = item.otherStates)

            StaleDevicesWarning(count = item.staleDevicesCount)
        }
    }
}

@Composable
private fun KServerConnectorCard(
    item: SyncListVM.ConnectorItem.KServer,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 500),
        label = "highlightBorder",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        border = if (isHighlighted) BorderStroke(2.dp, borderColor) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.OutdoorGrill,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        item.credentials.serverAdress.domain.endsWith(".darken.eu") -> {
                            "${stringResource(KServerR.string.sync_kserver_type_label)} (${item.credentials.serverAdress.domain})"
                        }
                        else -> {
                            "${stringResource(KServerR.string.sync_kserver_type_label)} (${item.credentials.serverAdress.address})"
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ConnectorStatusIndicators(isBusy = item.ourState.isBusy, isPaused = item.isPaused)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LabeledField(
                label = stringResource(R.string.sync_account_label),
                value = item.credentials.accountId.id,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LastSyncField(state = item.ourState)

            val quota = item.ourState.quota
            if (quota != null) {
                Spacer(modifier = Modifier.height(8.dp))

                LabeledField(
                    label = stringResource(CommonR.string.general_quota_label),
                    value = quota.let { stats ->
                        val total = Formatter.formatShortFileSize(context, stats.storageTotal)
                        val used = Formatter.formatShortFileSize(context, stats.storageUsed)
                        val free = Formatter.formatShortFileSize(context, stats.storageFree)
                        stringResource(R.string.sync_quota_storage_msg, free, used, total)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            DevicesField(state = item.ourState, otherStates = item.otherStates)

            StaleDevicesWarning(count = item.staleDevicesCount)
        }
    }
}

@Composable
private fun ConnectorStatusIndicators(isBusy: Boolean, isPaused: Boolean) {
    if (isBusy) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
    }
    if (isPaused) {
        Icon(
            imageVector = Icons.TwoTone.PauseCircle,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LabeledField(label: String, value: String) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LastSyncField(state: SyncConnectorState) {
    Text(
        text = stringResource(R.string.sync_last_action_label),
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = state.lastSyncAt
            ?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli()).toString() }
            ?: stringResource(R.string.sync_last_never_label),
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.lastError != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    if (state.lastError != null) {
        Text(
            text = state.lastError.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DevicesField(state: SyncConnectorState, otherStates: Collection<SyncConnectorState>) {
    Text(
        text = stringResource(R.string.sync_synced_devices_label),
        style = MaterialTheme.typography.labelMedium,
    )
    val devicesText = state.devices?.let { ourDevices ->
        var deviceString = pluralStringResource(R.plurals.general_devices_count_label, ourDevices.size, ourDevices.size)

        val devicesFromConnectors = otherStates.mapNotNull { it.devices }.flatten().toSet()
        val uniqueDevices = ourDevices - devicesFromConnectors
        if (uniqueDevices.isNotEmpty()) {
            val uniquesString = pluralStringResource(
                R.plurals.general_unique_devices_count_label,
                uniqueDevices.size,
                uniqueDevices.size,
            )
            deviceString += " ($uniquesString)"
        }
        deviceString
    } ?: stringResource(CommonR.string.general_na_label)

    Text(text = devicesText, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StaleDevicesWarning(count: Int) {
    if (count > 0) {
        Text(
            text = pluralStringResource(SyncR.plurals.sync_stale_devices_info_message, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun GDriveActionsDialog(
    item: SyncListVM.ConnectorItem.GDrive,
    onDismiss: () -> Unit,
    onTogglePause: () -> Unit,
    onForceSync: () -> Unit,
    onViewDevices: () -> Unit,
    onReset: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = buildString {
                        append(stringResource(GDriveR.string.sync_gdrive_type_label))
                        if (item.account.isAppDataScope) {
                            append(" (${stringResource(GDriveR.string.sync_gdrive_appdata_label)})")
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.account.email,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTogglePause)
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sync_connector_paused_label),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = item.isPaused,
                        onCheckedChange = { onTogglePause() },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onForceSync,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.general_sync_action))
                }

                FilledTonalButton(
                    onClick = onViewDevices,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !item.isPaused,
                ) {
                    Text(text = stringResource(R.string.sync_synced_devices_label))
                }

                FilledTonalButton(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.general_reset_action))
                }

                Button(
                    onClick = { showDisconnectConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(R.string.general_disconnect_action))
                }
            }
        },
        confirmButton = {},
    )

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            text = { Text(text = stringResource(GDriveR.string.sync_gdrive_disconnect_confirmation_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirmation = false
                    onDisconnect()
                }) {
                    Text(text = stringResource(R.string.general_disconnect_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmation = false }) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            text = { Text(text = stringResource(GDriveR.string.sync_gdrive_reset_confirmation_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onReset()
                }) {
                    Text(text = stringResource(R.string.general_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@Composable
private fun KServerActionsDialog(
    item: SyncListVM.ConnectorItem.KServer,
    onDismiss: () -> Unit,
    onTogglePause: () -> Unit,
    onForceSync: () -> Unit,
    onViewDevices: () -> Unit,
    onLinkNewDevice: () -> Unit,
    onReset: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showDisconnectConfirmation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${stringResource(KServerR.string.sync_kserver_type_label)} (${item.credentials.serverAdress.domain})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.credentials.accountId.id,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTogglePause)
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sync_connector_paused_label),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = item.isPaused,
                        onCheckedChange = { onTogglePause() },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onForceSync,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.general_sync_action))
                }

                FilledTonalButton(
                    onClick = onViewDevices,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !item.isPaused,
                ) {
                    Text(text = stringResource(R.string.sync_synced_devices_label))
                }

                FilledTonalButton(
                    onClick = onLinkNewDevice,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(KServerR.string.sync_kserver_link_device_action))
                }

                FilledTonalButton(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.general_reset_action))
                }

                Button(
                    onClick = { showDisconnectConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(R.string.general_disconnect_action))
                }
            }
        },
        confirmButton = {},
    )

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            text = { Text(text = stringResource(KServerR.string.sync_kserver_disconnect_confirmation_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirmation = false
                    onDisconnect()
                }) {
                    Text(text = stringResource(R.string.general_disconnect_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirmation = false }) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            text = { Text(text = stringResource(KServerR.string.sync_kserver_reset_confirmation_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onReset()
                }) {
                    Text(text = stringResource(R.string.general_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(text = stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun SyncListScreenPreview() = PreviewWrapper {
    SyncListScreen(
        state = SyncListVM.State(
            connectors = listOf(
                SyncListVM.ConnectorItem.KServer(
                    connectorId = ConnectorId(type = "kserver", subtype = "default", account = "preview-account"),
                    credentials = KServer.Credentials(
                        serverAdress = KServer.Address("prod.kserver.octi.darken.eu"),
                        accountId = KServer.Credentials.AccountId("preview-account-id"),
                        devicePassword = KServer.Credentials.DevicePassword("preview-password"),
                        encryptionKeyset = PayloadEncryption.KeySet(
                            type = "AES256_GCM",
                            key = "preview-key-data".encodeUtf8(),
                        ),
                    ),
                    ourState = KServerConnector.State(
                        activeActions = 0,
                        lastActionAt = Instant.now().minusSeconds(300),
                        devices = setOf(DeviceId("device-1"), DeviceId("device-2")),
                    ),
                    otherStates = emptyList(),
                    isPaused = false,
                    staleDevicesCount = 0,
                ),
            ),
        ),
        onNavigateUp = {},
        onAddConnector = {},
        onTogglePause = {},
        onForceSync = {},
        onViewDevices = {},
        onLinkNewDevice = {},
        onReset = {},
        onDisconnect = {},
    )
}

@Preview2
@Composable
private fun SyncListScreenEmptyPreview() = PreviewWrapper {
    SyncListScreen(
        state = SyncListVM.State(),
        onNavigateUp = {},
        onAddConnector = {},
        onTogglePause = {},
        onForceSync = {},
        onViewDevices = {},
        onLinkNewDevice = {},
        onReset = {},
        onDisconnect = {},
    )
}
