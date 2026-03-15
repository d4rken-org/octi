package eu.darken.octi.sync.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.DeviceId
import java.time.Instant

@Composable
fun DeviceActionsSheet(
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = device.metaInfo?.labelOrFallback ?: "?",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = device.deviceId.id,
                style = MaterialTheme.typography.labelMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.DeleteSweep,
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview2
@Composable
private fun DeviceActionsSheetPreview() = PreviewWrapper {
    val deviceId = DeviceId("device-abc-123")
    DeviceActionsSheet(
        device = SyncDevicesVM.DeviceItem(
            deviceId = deviceId,
            metaInfo = MetaInfo(
                deviceLabel = "Pixel 8",
                deviceId = deviceId,
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
        connectorType = "kserver",
        onDismiss = {},
        onDelete = {},
    )
}
