package eu.darken.octi.sync.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import eu.darken.octi.syncs.kserver.core.KServer
import eu.darken.octi.syncs.kserver.core.KServerConnector
import eu.darken.octi.syncs.kserver.R as KServerR
import okio.ByteString.Companion.encodeUtf8
import java.time.Instant

@Composable
fun KServerActionsSheet(
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "${stringResource(KServerR.string.sync_kserver_type_label)} (${item.credentials.serverAdress.domain})",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = item.credentials.accountId.id,
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

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
private fun KServerActionsSheetPreview() = PreviewWrapper {
    KServerActionsSheet(
        item = SyncListVM.ConnectorItem.KServer(
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
        onDismiss = {},
        onTogglePause = {},
        onForceSync = {},
        onViewDevices = {},
        onLinkNewDevice = {},
        onReset = {},
        onDisconnect = {},
    )
}
