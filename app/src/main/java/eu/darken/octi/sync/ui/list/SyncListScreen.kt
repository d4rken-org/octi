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
import androidx.compose.material.icons.twotone.Cloud
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.sync.core.ConnectorCapabilities
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.sync.core.LocalConnectorContributions
import eu.darken.octi.sync.core.OperationId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

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
        val subtitle = contribution.listCardSubtitle(item.connector)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                contribution.Icon(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contribution.listCardTitle(item.connector),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
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
private fun SyncListScreenPreview() = PreviewWrapper {
    val connectorId = ConnectorId(ConnectorType.GDRIVE, "appdata", "preview@example.com")
    CompositionLocalProvider(
        LocalConnectorContributions provides mapOf(
            ConnectorType.GDRIVE to previewContribution(
                type = ConnectorType.GDRIVE,
                label = "Google Drive",
                subtitle = "App-data scope",
            ),
        ),
    ) {
        SyncListScreen(
            state = SyncListVM.State(
                connectors = listOf(previewConnectorItem(connectorId)),
                highlightedConnectorIds = setOf(connectorId),
                isPro = true,
                deviceId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
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
}

private fun previewConnectorItem(connectorId: ConnectorId): SyncListVM.ConnectorItem {
    val connector = PreviewConnector(
        identifier = connectorId,
        accountLabel = "preview@example.com",
    )
    val state = PreviewConnectorState()
    return SyncListVM.ConnectorItem(
        connectorId = connectorId,
        connector = connector,
        ourState = state,
        storageStatus = StorageStatus.Ready(
            connectorId = connectorId,
            snapshot = StorageSnapshot(
                connectorId = connectorId,
                accountLabel = connector.accountLabel,
                usedBytes = 63_000_000,
                totalBytes = 16_000_000_000,
                availableBytes = 15_937_000_000,
                maxFileBytes = null,
                perFileOverheadBytes = 0,
                updatedAt = PREVIEW_NOW,
            ),
        ),
        otherStates = emptyList(),
        pauseReason = null,
        isPaused = true,
        isBusy = false,
    )
}

private fun previewContribution(
    type: ConnectorType,
    label: String,
    subtitle: String,
): ConnectorUiContribution = object : ConnectorUiContribution {
    override val type = type
    override val displayOrder = 0
    override val labelRes = R.string.sync_services_label
    override val descriptionRes = R.string.sync_services_label

    @Composable
    override fun Icon(modifier: Modifier, tint: Color) {
        Icon(
            imageVector = Icons.TwoTone.Cloud,
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
    }

    override fun addAccountDestination(): NavigationDestination = object : NavigationDestination {}

    @Composable
    override fun listCardTitle(connector: SyncConnector): String = label

    @Composable
    override fun listCardSubtitle(connector: SyncConnector): String = subtitle

    @Composable
    override fun listCardAccountValue(connector: SyncConnector): String = connector.accountLabel

    @Composable
    override fun ActionsSheet(
        connector: SyncConnector,
        state: SyncConnectorState,
        isPaused: Boolean,
        pauseReason: ConnectorPauseReason?,
        isPro: Boolean,
        onDismiss: () -> Unit,
        onTogglePause: () -> Unit,
        onForceSync: () -> Unit,
        onViewDevices: () -> Unit,
        onLinkNewDevice: () -> Unit,
        onReset: () -> Unit,
        onDisconnect: () -> Unit,
    ) = Unit
}

private class PreviewConnector(
    override val identifier: ConnectorId,
    override val accountLabel: String,
) : SyncConnector {
    override val capabilities: ConnectorCapabilities = ConnectorCapabilities.DEFAULT_FOR_TEST
    override val state: Flow<SyncConnectorState> = MutableStateFlow(PreviewConnectorState())
    override val data: Flow<SyncRead?> = MutableStateFlow(null)
    override val operations: StateFlow<List<ConnectorOperation>> = MutableStateFlow(emptyList())
    override val completions: SharedFlow<ConnectorOperation.Terminal> = MutableSharedFlow()

    override fun submit(command: ConnectorCommand): OperationId = OperationId.create()

    override suspend fun await(id: OperationId): ConnectorOperation.Terminal = ConnectorOperation.Succeeded(
        id = id,
        command = ConnectorCommand.Sync(),
        submittedAt = PREVIEW_NOW,
        startedAt = PREVIEW_NOW,
        finishedAt = PREVIEW_NOW,
    )

    override fun dismiss(id: OperationId) = Unit
}

private class PreviewConnectorState : SyncConnectorState {
    override val lastActionAt: Instant = PREVIEW_NOW
    override val lastError: Exception? = null
    override val isAvailable: Boolean = true
    override val deviceMetadata: List<DeviceMetadata> = listOf(
        DeviceMetadata(
            deviceId = DeviceId("pixel-8"),
            version = "1.0.0",
            platform = "Android",
            label = "Pixel 8",
            lastSeen = PREVIEW_NOW,
        ),
        DeviceMetadata(
            deviceId = DeviceId("galaxy-tab-s9"),
            version = "1.0.0",
            platform = "Android",
            label = "Galaxy Tab S9",
            lastSeen = PREVIEW_NOW,
        ),
    )
}

private val PREVIEW_NOW = Instant.parse("2026-05-01T12:00:00Z")
