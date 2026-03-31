package eu.darken.octi.sync.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import eu.darken.octi.R
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DeviceActionsSheet(
    device: SyncDevicesVM.DeviceItem,
    connectorType: ConnectorType?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val removeIsRevoke = when (connectorType) {
        ConnectorType.GDRIVE -> false
        ConnectorType.OCTISERVER -> true
        null -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (device.metaInfo?.deviceType) {
                        MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                        MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                        else -> Icons.TwoTone.QuestionMark
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = device.metaInfo?.labelOrFallback ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    device.serverVersion?.let { version ->
                        Text(
                            text = version,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    device.serverPlatform?.let { platform ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (platform.lowercase()) {
                                    "android" -> Icons.TwoTone.Android
                                    else -> Icons.TwoTone.QuestionMark
                                },
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = platform.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                text = device.deviceId.id,
                style = MaterialTheme.typography.labelMedium,
            )

            device.lastSeen?.let { lastSeen ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        SyncR.string.sync_device_last_seen_label,
                        DateUtils.getRelativeTimeSpanString(lastSeen.toEpochMilli()).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            device.serverAddedAt?.let { addedAt ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.sync_device_added_at_label,
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withZone(ZoneId.systemDefault())
                            .format(addedAt),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
            serverVersion = "0.14.0",
            serverAddedAt = Instant.now().minusSeconds(86400 * 30),
            serverPlatform = "android",
        ),
        connectorType = ConnectorType.OCTISERVER,
        onDismiss = {},
        onDelete = {},
    )
}
