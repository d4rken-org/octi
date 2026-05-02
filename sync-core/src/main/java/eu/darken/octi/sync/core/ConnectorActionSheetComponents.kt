package eu.darken.octi.sync.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.settings.UpgradeBadge
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.R
import kotlin.time.Clock

fun List<ConnectorOperation>.hasActivePauseToggle(): Boolean = any {
    it.isActive && (it.command is ConnectorCommand.Pause || it.command == ConnectorCommand.Resume)
}

@Composable
fun ConnectorActionSheetHeader(
    contribution: ConnectorUiContribution,
    title: String,
    subtitle: String?,
    account: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            contribution.Icon(
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = stringResource(R.string.sync_connector_account_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = account.ifBlank { stringResource(CommonR.string.general_na_label) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ConnectorOperationProgress(
    activeOperations: List<ConnectorOperation>,
    modifier: Modifier = Modifier,
) {
    val operation = activeOperations.displayOperation() ?: return

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = operation.label(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun ConnectorPauseRow(
    isPaused: Boolean,
    showPauseSwitch: Boolean,
    isInProgress: Boolean,
    onTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !isInProgress,
                onClick = onTogglePause,
            )
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.sync_connector_paused_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        when {
            isInProgress -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            showPauseSwitch -> Switch(
                checked = isPaused,
                onCheckedChange = null,
            )
            else -> UpgradeBadge(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

private val ConnectorOperation.isActive: Boolean
    get() = this is ConnectorOperation.Queued || this is ConnectorOperation.Processing

private fun List<ConnectorOperation>.displayOperation(): ConnectorOperation? =
    firstOrNull { it.isActive && it.command.prioritizeForDisplay } ?: firstOrNull { it.isActive }

private val ConnectorCommand.prioritizeForDisplay: Boolean
    get() = this is ConnectorCommand.Pause || this == ConnectorCommand.Resume || this == ConnectorCommand.Reset

@Composable
private fun ConnectorOperation.label(): String {
    val queued = this is ConnectorOperation.Queued
    return when (command) {
        is ConnectorCommand.Sync -> stringResource(
            if (queued) R.string.sync_connector_progress_sync_queued
            else R.string.sync_connector_progress_syncing,
        )
        is ConnectorCommand.Pause -> stringResource(
            if (queued) R.string.sync_connector_progress_pause_queued
            else R.string.sync_connector_progress_pausing,
        )
        ConnectorCommand.Resume -> stringResource(
            if (queued) R.string.sync_connector_progress_resume_queued
            else R.string.sync_connector_progress_resuming,
        )
        ConnectorCommand.Reset -> stringResource(
            if (queued) R.string.sync_connector_progress_reset_queued
            else R.string.sync_connector_progress_resetting,
        )
        is ConnectorCommand.DeleteDevice -> stringResource(
            if (queued) R.string.sync_connector_progress_device_update_queued
            else R.string.sync_connector_progress_device_updating,
        )
    }
}

@Preview2
@Composable
private fun ConnectorActionSheetNormalPreview() = PreviewWrapper {
    PreviewSheetContent(
        activeOperations = emptyList(),
        isPaused = false,
        showPauseSwitch = true,
    )
}

@Preview2
@Composable
private fun ConnectorActionSheetBusySyncPreview() = PreviewWrapper {
    PreviewSheetContent(
        activeOperations = listOf(
            ConnectorOperation.Processing(
                id = OperationId.create(),
                command = ConnectorCommand.Sync(),
                submittedAt = Clock.System.now(),
                startedAt = Clock.System.now(),
            ),
        ),
        isPaused = false,
        showPauseSwitch = true,
    )
}

@Preview2
@Composable
private fun ConnectorActionSheetPauseQueuedPreview() = PreviewWrapper {
    PreviewSheetContent(
        activeOperations = listOf(
            ConnectorOperation.Queued(
                id = OperationId.create(),
                command = ConnectorCommand.Pause(),
                submittedAt = Clock.System.now(),
            ),
        ),
        isPaused = false,
        showPauseSwitch = true,
    )
}

@Preview2
@Composable
private fun ConnectorActionSheetPausedNonProPreview() = PreviewWrapper {
    PreviewSheetContent(
        activeOperations = emptyList(),
        isPaused = true,
        showPauseSwitch = false,
    )
}

@Composable
private fun PreviewSheetContent(
    activeOperations: List<ConnectorOperation>,
    isPaused: Boolean,
    showPauseSwitch: Boolean,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        ConnectorActionSheetHeader(
            contribution = PreviewConnectorContribution,
            title = "Octi Server",
            subtitle = "sync.darken.eu",
            account = "account-1234",
        )
        Spacer(modifier = Modifier.height(16.dp))
        ConnectorOperationProgress(activeOperations = activeOperations)
        if (activeOperations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        ConnectorPauseRow(
            isPaused = isPaused,
            showPauseSwitch = showPauseSwitch,
            isInProgress = activeOperations.hasActivePauseToggle(),
            onTogglePause = {},
        )
    }
}

private object PreviewConnectorContribution : ConnectorUiContribution {
    override val type: ConnectorType = ConnectorType.OCTISERVER
    override val displayOrder: Int = 0
    override val labelRes: Int = R.string.sync_connector_paused_label
    override val descriptionRes: Int = R.string.sync_connector_paused_label

    @Composable
    override fun Icon(modifier: Modifier, tint: androidx.compose.ui.graphics.Color) {
        Icon(
            imageVector = Icons.TwoTone.Cloud,
            contentDescription = null,
            modifier = modifier,
            tint = tint,
        )
    }

    override fun addAccountDestination(): NavigationDestination = object : NavigationDestination {}

    @Composable
    override fun listCardTitle(connector: SyncConnector): String = "Octi Server"

    @Composable
    override fun listCardAccountValue(connector: SyncConnector): String = "account-1234"

    @Composable
    override fun ActionsSheet(
        connector: SyncConnector,
        state: SyncConnectorState,
        activeOperations: List<ConnectorOperation>,
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
