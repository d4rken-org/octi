package eu.darken.octi.modules.files.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.octi.modules.files.R
import eu.darken.octi.sync.core.blob.StorageStatus

/**
 * Inline highlight surfaced above the file list when at least one configured connector is below
 * the low-storage threshold. Shows only the offending connector(s); the full per-connector list
 * (including healthy ones) lives in [QuotaDetailsSheet], which the user reaches by tapping the
 * banner or the TopAppBar action.
 */
@Composable
internal fun QuotaWarningBanner(
    lowItems: List<StorageStatus>,
    collidingOctiSubtypes: Set<String>,
    onOpenDetails: () -> Unit,
) {
    if (lowItems.isEmpty()) return
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onOpenDetails() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.module_files_quota_low_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.module_files_quota_low_banner_message,
                    lowItems.size,
                    lowItems.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            lowItems.forEach { status ->
                QuotaRow(
                    status = status,
                    collidingOctiSubtypes = collidingOctiSubtypes,
                    context = context,
                    textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    lowTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    showLowStorageIcon = false,
                )
            }
        }
    }
}
