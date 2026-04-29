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
import androidx.compose.material.icons.twotone.PauseCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.error.localized
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.sync.core.LocalConnectorContributions
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.blob.StorageStatus

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
            onLinkNewDevice = { dest -> vm.linkNewDevice(dest) },
            onReset = { id -> vm.resetData(id) },
            onDisconnect = { id -> vm.disconnect(id) },
        )
    }
}

@Composable
fun SyncListScreen(
    state: SyncListVM.State,
    onNavigateUp: () -> Unit,
    onAddConnector: () -> Unit,
    onTogglePause: (ConnectorId) -> Unit,
    onForceSync: (ConnectorId) -> Unit,
    onViewDevices: (ConnectorId) -> Unit,
    onLinkNewDevice: (NavigationDestination) -> Unit,
    onReset: (ConnectorId) -> Unit,
    onDisconnect: (ConnectorId) -> Unit,
) {
    var showActionsForId by remember { mutableStateOf<ConnectorId?>(null) }
    val showActionsFor = showActionsForId?.let { id -> state.connectors.find { it.connectorId == id } }
    val contributions = LocalConnectorContributions.current

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
                key = { it.connectorId.idString },
            ) { item ->
                val contribution = contributions[item.connectorId.type] ?: run {
                    log(TAG, WARN) { "No ConnectorUiContribution for ${item.connectorId.type} — skipping card" }
                    return@items
                }
                val isHighlighted = state.highlightedConnectorIds.contains(item.connectorId)
                ConnectorCard(
                    item = item,
                    contribution = contribution,
                    isHighlighted = isHighlighted,
                    onClick = { showActionsForId = item.connectorId },
                )
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sync_device_id_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.deviceId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    showActionsFor?.let { item ->
        val contribution = contributions[item.connectorId.type] ?: return@let
        contribution.ActionsSheet(
            connector = item.connector,
            state = item.ourState,
            isPaused = item.isPaused,
            pauseReason = item.pauseReason,
            isPro = state.isPro,
            onDismiss = { showActionsForId = null },
            onTogglePause = { onTogglePause(item.connectorId) },
            onForceSync = {
                onForceSync(item.connectorId)
                showActionsForId = null
            },
            onViewDevices = {
                onViewDevices(item.connectorId)
                showActionsForId = null
            },
            onLinkNewDevice = {
                contribution.linkDeviceDestination(item.connector)?.let { onLinkNewDevice(it) }
                showActionsForId = null
            },
            onReset = {
                onReset(item.connectorId)
                showActionsForId = null
            },
            onDisconnect = {
                onDisconnect(item.connectorId)
                showActionsForId = null
            },
        )
    }
}

@Composable
private fun ConnectorCard(
    item: SyncListVM.ConnectorItem,
    contribution: eu.darken.octi.sync.core.ConnectorUiContribution,
    isHighlighted: Boolean,
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
                contribution.Icon(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = contribution.listCardTitle(item.connector),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ConnectorStatusIndicators(isBusy = item.isBusy, isPaused = item.isPaused)
            }

            Spacer(modifier = Modifier.height(8.dp))

            LabeledField(
                label = stringResource(R.string.sync_account_label),
                value = contribution.listCardAccountValue(item.connector),
            )

            Spacer(modifier = Modifier.height(8.dp))

            LastSyncField(state = item.ourState)

            Spacer(modifier = Modifier.height(8.dp))

            if (item.storageStatus !is StorageStatus.Unsupported) {
                LabeledField(
                    label = stringResource(CommonR.string.general_quota_label),
                    value = item.storageStatus.lastKnown
                        ?.let { snap ->
                            val total = Formatter.formatShortFileSize(context, snap.totalBytes)
                            val used = Formatter.formatShortFileSize(context, snap.usedBytes)
                            val free = Formatter.formatShortFileSize(context, snap.availableBytes)
                            stringResource(R.string.sync_quota_storage_msg, free, used, total)
                        }
                        ?: stringResource(CommonR.string.general_na_label),
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            DevicesField(state = item.ourState, otherStates = item.otherStates)

            ConnectorIssuesRow(issues = item.issues)
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
    val context = LocalContext.current
    val lastError = state.lastError
    Text(
        text = stringResource(R.string.sync_last_action_label),
        style = MaterialTheme.typography.labelMedium,
    )
    Text(
        text = state.lastSyncAt
            ?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilliseconds()).toString() }
            ?: stringResource(R.string.sync_last_never_label),
        style = MaterialTheme.typography.bodyMedium,
        color = if (lastError != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    if (lastError != null) {
        Text(
            text = lastError.localized(context).description.get(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DevicesField(state: SyncConnectorState, otherStates: Collection<SyncConnectorState>) {
    Text(
        text = stringResource(SyncR.string.sync_synced_devices_label),
        style = MaterialTheme.typography.labelMedium,
    )
    val ourDevices = state.deviceMetadata.map { it.deviceId }.toSet()
    val devicesText = ourDevices.takeIf { it.isNotEmpty() }?.let {
        var deviceString = pluralStringResource(R.plurals.general_devices_count_label, ourDevices.size, ourDevices.size)

        val devicesFromConnectors = otherStates.flatMap { it.deviceMetadata.map { m -> m.deviceId } }.toSet()
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
private fun ConnectorIssuesRow(issues: List<ConnectorIssue>) {
    val errors = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings = issues.filter { it.severity == IssueSeverity.WARNING }
    if (errors.isEmpty() && warnings.isEmpty()) return

    val context = LocalContext.current

    Column(modifier = Modifier.padding(top = 4.dp)) {
        if (errors.isNotEmpty()) {
            val summary = errors
                .groupBy { it.label.get(context) }
                .map { (typeLabel, group) -> "${group.size} $typeLabel" }
                .joinToString(", ")
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (warnings.isNotEmpty()) {
            val summary = warnings
                .groupBy { it.label.get(context) }
                .map { (typeLabel, group) -> "${group.size} $typeLabel" }
                .joinToString(", ")
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private val TAG = logTag("Sync", "List", "Screen")

@Preview2
@Composable
private fun SyncListScreenEmptyPreview() = PreviewWrapper {
    SyncListScreen(
        state = SyncListVM.State(deviceId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
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
