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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.PhoneAndroid
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material.icons.twotone.Tablet
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.R as SyncR
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.IssueSeverity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun DeviceRow(
    item: SyncDevicesVM.DeviceItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (item.metaInfo?.deviceType) {
                        MetaInfo.DeviceType.PHONE -> Icons.TwoTone.PhoneAndroid
                        MetaInfo.DeviceType.TABLET -> Icons.TwoTone.Tablet
                        else -> Icons.TwoTone.QuestionMark
                    },
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.metaInfo?.labelOrFallback ?: item.deviceId.id.take(8),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.metaInfo?.let { meta ->
                        Text(
                            text = "${meta.deviceManufacturer} · Android ${meta.androidVersionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    item.serverVersion?.let { version ->
                        Text(
                            text = version,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item.serverPlatform?.let { platform ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (platform.lowercase()) {
                                    "android" -> Icons.TwoTone.Android
                                    else -> Icons.TwoTone.QuestionMark
                                },
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = platform.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item.lastSeen?.let { lastSeen ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.TwoTone.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            SyncR.string.sync_device_last_seen_label,
                            DateUtils.getRelativeTimeSpanString(lastSeen.toEpochMilliseconds()).toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Icon(
                        imageVector = Icons.TwoTone.Error,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = error.localizedMessage
                            ?: stringResource(eu.darken.octi.common.R.string.general_error_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 10,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item.issues.forEach { issue ->
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Icon(
                        imageVector = when (issue.severity) {
                            IssueSeverity.ERROR -> Icons.TwoTone.Error
                            IssueSeverity.WARNING -> Icons.TwoTone.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = when (issue.severity) {
                            IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                            IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = issue.label.get(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (issue.severity) {
                            IssueSeverity.ERROR -> MaterialTheme.colorScheme.error
                            IssueSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun DeviceRowPreview() = PreviewWrapper {
    val deviceId1 = DeviceId("device-abc-123")
    val deviceId2 = DeviceId("device-def-456")
    val deviceId3 = DeviceId("device-ghi-789")
    val previewConnectorId = ConnectorId(
        type = ConnectorType.GDRIVE,
        subtype = "preview",
        account = "preview@example.com",
    )

    fun previewIssue(sev: IssueSeverity, text: String): ConnectorIssue = object : ConnectorIssue {
        override val connectorId = previewConnectorId
        override val deviceId = deviceId1
        override val severity = sev
        override val label: CaString = text.toCaString()
        override val description: CaString = CaString.EMPTY
    }

    Column {
        DeviceRow(
            item = SyncDevicesVM.DeviceItem(
                deviceId = deviceId1,
                metaInfo = MetaInfo(
                    deviceLabel = "Pixel 9 Pro",
                    deviceId = deviceId1,
                    octiVersionName = "1.0.0-beta1",
                    octiGitSha = "abc1234",
                    deviceManufacturer = "Google",
                    deviceName = "Pixel 9 Pro",
                    deviceType = MetaInfo.DeviceType.PHONE,
                    deviceBootedAt = Clock.System.now(),
                    androidVersionName = "15",
                    androidApiLevel = 35,
                    androidSecurityPatch = "2025-01-05",
                ),
                lastSeen = Clock.System.now(),
                error = null,
                serverVersion = "1.0.0-beta1",
                serverAddedAt = Clock.System.now(),
                serverPlatform = "android",
                issues = listOf(previewIssue(IssueSeverity.WARNING, "Stale device")),
            ),
            onClick = {},
        )
        DeviceRow(
            item = SyncDevicesVM.DeviceItem(
                deviceId = deviceId2,
                metaInfo = MetaInfo(
                    deviceLabel = "Galaxy Tab S9",
                    deviceId = deviceId2,
                    octiVersionName = "0.13.0",
                    octiGitSha = "def5678",
                    deviceManufacturer = "Samsung",
                    deviceName = "Galaxy Tab S9",
                    deviceType = MetaInfo.DeviceType.TABLET,
                    deviceBootedAt = Clock.System.now(),
                    androidVersionName = "14",
                    androidApiLevel = 34,
                    androidSecurityPatch = "2024-01-01",
                ),
                lastSeen = Clock.System.now() - (86400 * 60).seconds,
                error = RuntimeException("Connection timed out"),
                serverVersion = "0.13.0",
                serverAddedAt = Clock.System.now() - (86400 * 60).seconds,
                serverPlatform = "android",
                issues = listOf(previewIssue(IssueSeverity.ERROR, "Server storage low")),
            ),
            onClick = {},
        )
        DeviceRow(
            item = SyncDevicesVM.DeviceItem(
                deviceId = deviceId3,
                metaInfo = null,
                lastSeen = null,
                error = null,
                serverVersion = null,
                serverAddedAt = null,
                serverPlatform = null,
            ),
            onClick = {},
        )
    }
}
