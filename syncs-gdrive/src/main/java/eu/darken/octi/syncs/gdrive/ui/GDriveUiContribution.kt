package eu.darken.octi.syncs.gdrive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon as M3Icon
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
import androidx.compose.ui.res.painterResource
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
import eu.darken.octi.syncs.gdrive.R
import eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveUiContribution @Inject constructor() : ConnectorUiContribution {
    override val type = ConnectorType.GDRIVE
    override val displayOrder = 10
    override val labelRes = R.string.sync_gdrive_type_label
    override val descriptionRes = R.string.sync_gdrive_type_appdata_description

    @Composable
    override fun Icon(modifier: Modifier, tint: Color) {
        M3Icon(
            painter = painterResource(R.drawable.ic_baseline_gdrive_24),
            contentDescription = null,
            tint = tint,
            modifier = modifier,
        )
    }

    override fun addAccountDestination(): NavigationDestination = Nav.Sync.AddGDrive

    @Composable
    override fun listCardTitle(connector: SyncConnector): String = stringResource(R.string.sync_gdrive_type_label)

    @Composable
    override fun listCardSubtitle(connector: SyncConnector): String = stringResource(R.string.sync_gdrive_appdata_label)

    @Composable
    override fun listCardAccountValue(connector: SyncConnector): String {
        val gdrive = connector as? GDriveAppDataConnector ?: return ""
        return gdrive.account.email
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
        val gdrive = connector as? GDriveAppDataConnector ?: return
        var showDisconnectConfirmation by remember { mutableStateOf(false) }
        var showResetConfirmation by remember { mutableStateOf(false) }
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
                    contribution = this@GDriveUiContribution,
                    title = stringResource(R.string.sync_gdrive_type_label),
                    subtitle = stringResource(R.string.sync_gdrive_appdata_label),
                    account = gdrive.account.email,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConnectorOperationProgress(activeOperations = activeOperations)
                if (activeOperations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                ConnectorPauseRow(
                    isPaused = isPaused,
                    showPauseSwitch = isPro,
                    isInProgress = pauseToggleInProgress,
                    onTogglePause = onTogglePause,
                )

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
                text = { Text(text = stringResource(R.string.sync_gdrive_disconnect_confirmation_desc)) },
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
                text = { Text(text = stringResource(R.string.sync_gdrive_reset_confirmation_desc)) },
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
