package eu.darken.octi.modules.files.ui.list

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.PieChart
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.modules.files.R
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.isLowStorage

/**
 * Bottom sheet listing every configured connector's storage status as a card with type chip,
 * account/subtype, quota text, and a usage progress bar. Low connectors get a `tertiaryContainer`
 * highlight + tertiary-tinted progress bar; healthy ones use `surface` + `primary` progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuotaDetailsSheet(
    items: List<StorageStatus>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val collidingOctiSubtypes = collidingOctiSubtypesFor(items)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.PieChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.module_files_quota_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { status ->
                QuotaConnectorCard(
                    status = status,
                    collidingOctiSubtypes = collidingOctiSubtypes,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun QuotaConnectorCard(
    status: StorageStatus,
    collidingOctiSubtypes: Set<String>,
) {
    val context = LocalContext.current
    val snapshot = status.lastKnown
    val isLow = snapshot?.isLowStorage() == true
    val isLoading = status is StorageStatus.Loading
    val isUnavailable = status is StorageStatus.Unavailable

    val cardColors = if (isLow) {
        CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    } else {
        CardDefaults.outlinedCardColors()
    }
    val progressColor = if (isLow) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    OutlinedCard(
        colors = cardColors,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectorTypeChip(connectorId = status.connectorId)
                Spacer(modifier = Modifier.weight(1f))
                CardStatusGlyph(isLow = isLow, isLoading = isLoading && snapshot == null, isUnavailable = isUnavailable)
            }

            val accountText = quotaConnectorAccountText(
                connectorId = status.connectorId,
                accountLabel = snapshot?.accountLabel,
                collidingOctiSubtypes = collidingOctiSubtypes,
            )
            if (accountText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = accountText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                snapshot != null -> {
                    val fraction = if (snapshot.totalBytes > 0) {
                        (snapshot.usedBytes.toFloat() / snapshot.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        text = stringResource(
                            R.string.module_files_quota_item_short,
                            Formatter.formatFileSize(context, snapshot.usedBytes),
                            Formatter.formatFileSize(context, snapshot.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = progressColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                isLoading -> {
                    Text(
                        text = stringResource(R.string.module_files_quota_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                isUnavailable -> {
                    Text(
                        text = stringResource(R.string.module_files_quota_unavailable_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectorTypeChip(connectorId: ConnectorId) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = quotaConnectorTypeLabel(connectorId),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CardStatusGlyph(isLow: Boolean, isLoading: Boolean, isUnavailable: Boolean) {
    when {
        isLow -> Icon(
            imageVector = Icons.TwoTone.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        isLoading -> CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 1.5.dp,
        )
        isUnavailable -> Icon(
            imageVector = Icons.TwoTone.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
