package eu.darken.octi.modules.files.ui.list

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.modules.files.R
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.isLowStorage

/**
 * Compute the set of OctiServer subtypes that appear with more than one configured account, so
 * row labels disambiguate by account suffix only when needed.
 */
internal fun collidingOctiSubtypesFor(items: List<StorageStatus>): Set<String> = items
    .filter { it.connectorId.type == ConnectorType.OCTISERVER }
    .groupingBy { it.connectorId.subtype }
    .eachCount()
    .filterValues { it > 1 }
    .keys

/**
 * One row in the quota card / sheet. Caller controls colour palette (`textColor`) so the same row
 * works in the inline warning banner (on `tertiaryContainer`) and in the details sheet (on the
 * default surface). Low rows in the sheet get a leading warning icon — non-colour cue for
 * accessibility — controlled by [showLowStorageIcon].
 */
@Composable
internal fun QuotaRow(
    status: StorageStatus,
    collidingOctiSubtypes: Set<String>,
    context: Context,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    lowTextColor: Color = MaterialTheme.colorScheme.error,
    showLowStorageIcon: Boolean = false,
) {
    val label = quotaConnectorLabel(status.connectorId, status.lastKnown?.accountLabel, collidingOctiSubtypes)
    val snapshot: StorageSnapshot? = status.lastKnown
    val isLow = snapshot?.isLowStorage() == true
    val isUnavailable = status is StorageStatus.Unavailable
    val color = when {
        isLow -> lowTextColor
        isUnavailable -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> textColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            status is StorageStatus.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            isLow && showLowStorageIcon -> {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            isUnavailable -> {
                Icon(
                    imageVector = Icons.TwoTone.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
        val text = if (snapshot != null) {
            stringResource(
                R.string.module_files_quota_item,
                label,
                Formatter.formatFileSize(context, snapshot.usedBytes),
                Formatter.formatFileSize(context, snapshot.totalBytes),
            )
        } else {
            // Loading-from-cold or Unavailable with no last-known: render just the label so the
            // row still anchors visually until data arrives.
            label
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
internal fun quotaConnectorLabel(
    connectorId: ConnectorId,
    accountLabel: String?,
    collidingOctiSubtypes: Set<String>,
): String = when (connectorId.type) {
    ConnectorType.GDRIVE -> {
        if (accountLabel.isNullOrBlank()) {
            stringResource(R.string.module_files_quota_label_gdrive)
        } else {
            stringResource(R.string.module_files_quota_label_gdrive_with_account, accountLabel)
        }
    }
    ConnectorType.OCTISERVER -> {
        val subtype = connectorId.subtype
        if (subtype in collidingOctiSubtypes && !accountLabel.isNullOrBlank()) {
            stringResource(R.string.module_files_quota_label_octiserver_with_account, subtype, accountLabel)
        } else {
            subtype
        }
    }
}

/** Short type label rendered in the connector card's chip. */
@Composable
internal fun quotaConnectorTypeLabel(connectorId: ConnectorId): String = when (connectorId.type) {
    ConnectorType.GDRIVE -> stringResource(R.string.module_files_quota_label_gdrive)
    ConnectorType.OCTISERVER -> stringResource(R.string.module_files_quota_type_octiserver)
}

/**
 * Secondary text rendered under the connector card's chip — the human-meaningful identifier for
 * this specific connector instance. Returns null when there's nothing useful to show (e.g. GDrive
 * without a known account label — the chip already says "Google Drive").
 */
@Composable
internal fun quotaConnectorAccountText(
    connectorId: ConnectorId,
    accountLabel: String?,
    collidingOctiSubtypes: Set<String>,
): String? = when (connectorId.type) {
    ConnectorType.GDRIVE -> accountLabel?.takeUnless { it.isBlank() }
    ConnectorType.OCTISERVER -> {
        val subtype = connectorId.subtype
        if (subtype in collidingOctiSubtypes && !accountLabel.isNullOrBlank()) {
            stringResource(R.string.module_files_quota_label_octiserver_with_account, subtype, accountLabel)
        } else {
            subtype
        }
    }
}
