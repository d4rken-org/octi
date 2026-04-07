package eu.darken.octi.main.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.sync.core.ClockAnalyzer.ClockAnalysis
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.R as SyncR
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun ClockDiscrepancySheet(
    analysis: ClockAnalysis,
    deviceNames: Map<DeviceId, String>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ClockDiscrepancyContent(analysis, deviceNames)
    }
}

@Composable
private fun ClockDiscrepancyContent(
    analysis: ClockAnalysis,
    deviceNames: Map<DeviceId, String> = emptyMap(),
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.TwoTone.Schedule,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(SyncR.string.sync_clock_discrepancy_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        val explanationText = when {
            !analysis.canDetermineSource -> {
                stringResource(SyncR.string.sync_clock_discrepancy_uncertain)
            }

            analysis.isCurrentDeviceSuspect -> {
                stringResource(SyncR.string.sync_clock_discrepancy_current_suspect)
            }

            analysis.suspectDeviceIds.isNotEmpty() -> {
                val names = analysis.suspectDeviceIds.joinToString(", ") { id ->
                    deviceNames[id] ?: id.id
                }
                stringResource(SyncR.string.sync_clock_discrepancy_remote_suspect, names)
            }

            else -> stringResource(SyncR.string.sync_clock_discrepancy_uncertain)
        }

        Text(
            text = explanationText,
            style = MaterialTheme.typography.bodyMedium,
        )

        analysis.localClockOffset?.let { offset ->
            val absSeconds = offset.absoluteValue.inWholeSeconds
            val offsetText = when {
                absSeconds >= 3600 -> "${absSeconds / 3600}h ${(absSeconds % 3600) / 60}m"
                absSeconds >= 60 -> "${absSeconds / 60}m ${absSeconds % 60}s"
                else -> "${absSeconds}s"
            }
            val directionStringRes = if (!offset.isNegative()) {
                SyncR.string.sync_clock_discrepancy_server_offset_ahead
            } else {
                SyncR.string.sync_clock_discrepancy_server_offset_behind
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(directionStringRes, offsetText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Preview2
@Composable
private fun UncertainPreview() = PreviewWrapper {
    ClockDiscrepancyContent(
        analysis = ClockAnalysis(
            skewedDeviceIds = setOf(DeviceId("device-a")),
            suspectDeviceIds = emptySet(),
            isCurrentDeviceSuspect = false,
            canDetermineSource = false,
            localClockOffset = null,
        ),
    )
}

@Preview2
@Composable
private fun CurrentSuspectPreview() = PreviewWrapper {
    ClockDiscrepancyContent(
        analysis = ClockAnalysis(
            skewedDeviceIds = setOf(DeviceId("device-a"), DeviceId("device-b")),
            suspectDeviceIds = emptySet(),
            isCurrentDeviceSuspect = true,
            canDetermineSource = true,
            localClockOffset = (-30).minutes,
        ),
    )
}

@Preview2
@Composable
private fun RemoteSuspectPreview() = PreviewWrapper {
    ClockDiscrepancyContent(
        analysis = ClockAnalysis(
            skewedDeviceIds = setOf(DeviceId("device-a")),
            suspectDeviceIds = setOf(DeviceId("device-a")),
            isCurrentDeviceSuspect = false,
            canDetermineSource = true,
            localClockOffset = 5.seconds,
        ),
        deviceNames = mapOf(DeviceId("device-a") to "Pixel Tablet"),
    )
}
