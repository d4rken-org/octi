package eu.darken.octi.syncs.octiserver.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationDestination
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorActionSheetHeader
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.ConnectorOperationProgress
import eu.darken.octi.sync.core.ConnectorPauseReason
import eu.darken.octi.sync.core.ConnectorPauseRow
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.hasActivePauseToggle
import eu.darken.octi.syncs.octiserver.R
import eu.darken.octi.syncs.octiserver.core.OctiServerConnector
import eu.darken.octi.syncs.octiserver.core.OctiServerIssue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OctiServerUiContribution @Inject constructor() : ConnectorUiContribution {
    override val type = ConnectorType.OCTISERVER
    override val displayOrder = 20
    override val labelRes = R.string.sync_octiserver_type_label
    override val descriptionRes = R.string.sync_octiserver_type_description

    @Composable
    override fun Icon(modifier: Modifier, tint: Color) {
        OctiServerIcon(modifier = modifier, tint = tint)
    }

    override fun addAccountDestination(): NavigationDestination = Nav.Sync.AddOctiServer

    override fun joinDeviceDestination(): NavigationDestination = Nav.Sync.OctiServerLinkClient

    override fun linkDeviceDestination(connector: SyncConnector): NavigationDestination? {
        val octi = connector as? OctiServerConnector ?: return null
        return Nav.Sync.OctiServerLinkHost(octi.identifier.idString)
    }

    @Composable
    override fun listCardTitle(connector: SyncConnector): String = stringResource(R.string.sync_octiserver_type_label)

    @Composable
    override fun listCardSubtitle(connector: SyncConnector): String {
        val octi = connector as? OctiServerConnector ?: return ""
        return octi.serverDisplay()
    }

    @Composable
    override fun listCardAccountValue(connector: SyncConnector): String {
        val octi = connector as? OctiServerConnector ?: return ""
        return octi.credentials.accountId.id
    }

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
    ) {
        val octi = connector as? OctiServerConnector ?: return
        var showDisconnectConfirmation by remember { mutableStateOf(false) }
        var showResetConfirmation by remember { mutableStateOf(false) }
        val unknownDeviceIssue = state.issues.filterIsInstance<OctiServerIssue.CurrentDeviceNotRegistered>().firstOrNull()
        val context = LocalContext.current
        val canTogglePause = isPro || pauseReason == ConnectorPauseReason.AuthIssue
        val isBusy = activeOperations.isNotEmpty()
        val pauseToggleInProgress = activeOperations.hasActivePauseToggle()

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                ConnectorActionSheetHeader(
                    contribution = this@OctiServerUiContribution,
                    title = stringResource(R.string.sync_octiserver_type_label),
                    subtitle = octi.serverDisplay(),
                    account = octi.credentials.accountId.id,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConnectorOperationProgress(activeOperations = activeOperations)
                if (activeOperations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (unknownDeviceIssue != null) {
                    Text(
                        text = unknownDeviceIssue.label.get(context),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = unknownDeviceIssue.description.get(context),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                ConnectorPauseRow(
                    isPaused = isPaused,
                    showPauseSwitch = canTogglePause,
                    isInProgress = pauseToggleInProgress,
                    onTogglePause = onTogglePause,
                )

                if (unknownDeviceIssue != null && isPaused) {
                    FilledTonalButton(
                        onClick = onTogglePause,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !pauseToggleInProgress,
                    ) {
                        Text(text = stringResource(R.string.sync_octiserver_resume_sync_action))
                    }
                }

                Button(
                    onClick = onForceSync,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPaused && !isBusy,
                ) {
                    Text(text = stringResource(CommonR.string.general_sync_action))
                }

                FilledTonalButton(
                    onClick = onViewDevices,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPaused && !isBusy,
                ) {
                    Text(text = stringResource(SyncR.string.sync_synced_devices_label))
                }

                FilledTonalButton(
                    onClick = onLinkNewDevice,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPaused && !isBusy,
                ) {
                    Text(text = stringResource(R.string.sync_octiserver_link_device_action))
                }

                FilledTonalButton(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isPaused && !isBusy,
                ) {
                    Text(text = stringResource(CommonR.string.general_reset_action))
                }

                Button(
                    onClick = { showDisconnectConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(CommonR.string.general_disconnect_action))
                }
            }
        }

        if (showDisconnectConfirmation) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirmation = false },
                text = { Text(text = stringResource(R.string.sync_octiserver_disconnect_confirmation_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectConfirmation = false
                        onDisconnect()
                    }) {
                        Text(text = stringResource(CommonR.string.general_disconnect_action))
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
                text = { Text(text = stringResource(R.string.sync_octiserver_reset_confirmation_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirmation = false
                        onReset()
                    }) {
                        Text(text = stringResource(CommonR.string.general_reset_action))
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
}

/**
 * For trusted darken.eu-hosted servers, show just the domain (clean UX). For custom servers,
 * show the full address including protocol/port so users can identify non-standard setups.
 */
private fun OctiServerConnector.serverDisplay(): String = when {
    credentials.serverAdress.domain.endsWith(".darken.eu") -> credentials.serverAdress.domain
    else -> credentials.serverAdress.address
}
