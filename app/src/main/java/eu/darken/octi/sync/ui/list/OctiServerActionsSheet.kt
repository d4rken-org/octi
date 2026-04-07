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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceMetadata
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import eu.darken.octi.syncs.octiserver.core.OctiServer
import eu.darken.octi.syncs.octiserver.core.OctiServerConnector
import eu.darken.octi.syncs.octiserver.R as OctiServerR
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Composable
fun OctiServerActionsSheet(
    item: SyncListVM.ConnectorItem.OctiServer,
    isPro: Boolean,
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
                text = "${stringResource(OctiServerR.string.sync_octiserver_type_label)} (${item.credentials.serverAdress.domain})",
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
                if (isPro) {
                    Switch(
                        checked = item.isPaused,
                        onCheckedChange = null,
                    )
                } else {
                    Icon(
                        imageVector = Icons.TwoTone.Stars,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
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
                Text(text = stringResource(OctiServerR.string.sync_octiserver_link_device_action))
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
            text = { Text(text = stringResource(OctiServerR.string.sync_octiserver_disconnect_confirmation_desc)) },
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
            text = { Text(text = stringResource(OctiServerR.string.sync_octiserver_reset_confirmation_desc)) },
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
private fun OctiServerActionsSheetPreview() = PreviewWrapper {
    OctiServerActionsSheet(
        isPro = true,
        item = SyncListVM.ConnectorItem.OctiServer(
            connectorId = ConnectorId(type = ConnectorType.OCTISERVER, subtype = "default", account = "preview-account"),
            credentials = OctiServer.Credentials(
                serverAdress = OctiServer.Address("prod.kserver.octi.darken.eu"),
                accountId = OctiServer.Credentials.AccountId("preview-account-id"),
                devicePassword = OctiServer.Credentials.DevicePassword("preview-password"),
                encryptionKeyset = PayloadEncryption.KeySet(
                    type = "AES256_GCM",
                    key = "preview-key-data".encodeUtf8(),
                ),
            ),
            ourState = OctiServerConnector.State(
                activeActions = 0,
                lastActionAt = Clock.System.now() - 300.seconds,
                deviceMetadata = listOf(
                    DeviceMetadata(deviceId = DeviceId("device-1")),
                    DeviceMetadata(deviceId = DeviceId("device-2")),
                ),
            ),
            otherStates = emptyList(),
            isPaused = false,
            issues = emptyList(),
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
