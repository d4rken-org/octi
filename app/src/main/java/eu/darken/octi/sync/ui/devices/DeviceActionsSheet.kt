package eu.darken.octi.sync.ui.devices

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.CopyableDetailRow
import eu.darken.octi.common.compose.DetailRow
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceRemovalPolicy
import eu.darken.octi.sync.core.IssueSeverity
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Composable
fun DeviceActionsSheet(
    device: SyncDevicesVM.DeviceItem,
    removalPolicy: DeviceRemovalPolicy?,
    isPaused: Boolean,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val removeIsRevoke = when (removalPolicy) {
        DeviceRemovalPolicy.REMOVE_LOCAL_ONLY -> false
        DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE -> true
        null -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            DeviceHeader(
                item = device,
                titleStyle = MaterialTheme.typography.titleMedium,
            )

            device.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.TwoTone.Error,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = error.localizedMessage
                            ?: stringResource(CommonR.string.general_error_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (device.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            device.issues.forEach { issue ->
                val tint = when (issue.severity) {
                    IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                    IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = when (issue.severity) {
                            IssueSeverity.ERROR -> Icons.TwoTone.Error
                            IssueSeverity.WARNING -> Icons.TwoTone.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tint,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = issue.description.get(context).ifBlank { issue.label.get(context) },
                        style = MaterialTheme.typography.bodySmall,
                        color = tint,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            device.metaInfo?.let { meta ->
                DetailRow(
                    label = stringResource(R.string.sync_device_model_label),
                    value = meta.deviceName,
                )
                DetailRow(
                    label = stringResource(R.string.sync_device_android_version_label),
                    value = "${meta.androidVersionName} (API ${meta.androidApiLevel})",
                )
                meta.androidSecurityPatch?.takeIf { it.isNotBlank() }?.let { patch ->
                    DetailRow(
                        label = stringResource(R.string.sync_device_security_patch_label),
                        value = patch,
                    )
                }
                DetailRow(
                    label = stringResource(R.string.sync_device_booted_label),
                    value = DateUtils.getRelativeTimeSpanString(meta.deviceBootedAt.toEpochMilliseconds()).toString(),
                )
            }

            CopyableDetailRow(
                label = stringResource(R.string.sync_device_id_label),
                value = device.deviceId.id,
                copyable = true,
                showMessage = showMessage,
                copiedMessage = stringResource(R.string.sync_device_id_copied_toast),
            )

            device.metaInfo?.let { meta ->
                DetailRow(
                    label = stringResource(R.string.sync_device_octi_version_label),
                    value = "${meta.octiVersionName} (${meta.octiGitSha})",
                )
            }

            device.serverAddedAt?.let { addedAt ->
                DetailRow(
                    label = stringResource(R.string.sync_device_added_label),
                    value = DateUtils.formatDateTime(
                        context,
                        addedAt.toEpochMilliseconds(),
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
                    ),
                )
            }

            device.lastSeen?.let { lastSeen ->
                DetailRow(
                    label = stringResource(R.string.sync_device_last_seen_short_label),
                    value = DateUtils.getRelativeTimeSpanString(lastSeen.toEpochMilliseconds()).toString(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDelete,
                enabled = !isPaused && !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Icon(
                        imageVector = Icons.TwoTone.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
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

private val previewDeviceId = DeviceId("device-abc-123")
private val previewConnectorId = ConnectorId(
    type = ConnectorType.GDRIVE,
    subtype = "preview",
    account = "preview@example.com",
)

private val previewMeta = MetaInfo(
    deviceLabel = "Pixel 8",
    deviceId = previewDeviceId,
    octiVersionName = "0.14.0",
    octiGitSha = "abc1234",
    deviceManufacturer = "Google",
    deviceName = "Pixel 8",
    deviceType = MetaInfo.DeviceType.PHONE,
    deviceBootedAt = Clock.System.now() - (86400 * 5).seconds,
    androidVersionName = "14",
    androidApiLevel = 34,
    androidSecurityPatch = "2024-01-05",
)

private fun previewDevice(
    metaInfo: MetaInfo? = previewMeta,
    error: Exception? = null,
    issues: List<ConnectorIssue> = emptyList(),
): SyncDevicesVM.DeviceItem = SyncDevicesVM.DeviceItem(
    deviceId = previewDeviceId,
    metaInfo = metaInfo,
    lastSeen = Clock.System.now(),
    error = error,
    serverVersion = "0.14.0",
    serverAddedAt = Clock.System.now() - (86400 * 30).seconds,
    serverPlatform = "android",
    issues = issues,
)

private fun previewIssue(severity: IssueSeverity, label: String, description: String): ConnectorIssue =
    object : ConnectorIssue {
        override val connectorId = previewConnectorId
        override val deviceId = previewDeviceId
        override val severity = severity
        override val label: CaString = label.toCaString()
        override val description: CaString = description.toCaString()
    }

@Preview2
@Composable
private fun DeviceActionsSheetPreview() = PreviewWrapper {
    DeviceActionsSheet(
        device = previewDevice(
            issues = listOf(previewIssue(IssueSeverity.WARNING, "Stale device", "Device hasn't synced in 14 days")),
        ),
        removalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
        isPaused = false,
        isDeleting = false,
        onDismiss = {},
        onDelete = {},
        showMessage = {},
    )
}

@Preview2
@Composable
private fun DeviceActionsSheetDeletingPreview() = PreviewWrapper {
    DeviceActionsSheet(
        device = previewDevice(),
        removalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
        isPaused = false,
        isDeleting = true,
        onDismiss = {},
        onDelete = {},
        showMessage = {},
    )
}

@Preview2
@Composable
private fun DeviceActionsSheetErrorPreview() = PreviewWrapper {
    DeviceActionsSheet(
        device = previewDevice(
            metaInfo = null,
            error = RuntimeException("Failed to deserialize MetaInfo: unknown field 'foo'"),
        ),
        removalPolicy = DeviceRemovalPolicy.REMOVE_LOCAL_ONLY,
        isPaused = false,
        isDeleting = false,
        onDismiss = {},
        onDelete = {},
        showMessage = {},
    )
}

@Preview2
@Composable
private fun DeviceActionsSheetNoSecurityPatchPreview() = PreviewWrapper {
    DeviceActionsSheet(
        device = previewDevice(
            metaInfo = previewMeta.copy(androidSecurityPatch = null),
        ),
        removalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
        isPaused = false,
        isDeleting = false,
        onDismiss = {},
        onDelete = {},
        showMessage = {},
    )
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 720, heightDp = 360, showBackground = true)
@Composable
private fun DeviceActionsSheetLandscapePreview() = PreviewWrapper {
    DeviceActionsSheet(
        device = previewDevice(),
        removalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
        isPaused = false,
        isDeleting = false,
        onDismiss = {},
        onDelete = {},
        showMessage = {},
    )
}

@androidx.compose.ui.tooling.preview.Preview(fontScale = 1.5f, showBackground = true)
@Composable
private fun DeviceActionsSheetLargeFontPreview() = PreviewWrapper {
    DeviceActionsSheet(
        device = previewDevice(),
        removalPolicy = DeviceRemovalPolicy.REMOVE_AND_REVOKE_REMOTE,
        isPaused = false,
        isDeleting = false,
        onDismiss = {},
        onDelete = {},
        showMessage = {},
    )
}
