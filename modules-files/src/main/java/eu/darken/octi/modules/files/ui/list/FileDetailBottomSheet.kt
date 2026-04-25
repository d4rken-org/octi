package eu.darken.octi.modules.files.ui.list

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.modules.files.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailBottomSheet(
    item: FileShareListVM.FileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.sharedFile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            DetailRow(
                label = stringResource(R.string.module_files_sheet_property_size),
                value = Formatter.formatFileSize(context, item.sharedFile.size),
            )
            DetailRow(
                label = stringResource(R.string.module_files_sheet_property_type),
                value = item.sharedFile.mimeType.ifBlank { "application/octet-stream" },
            )
            DetailRow(
                label = stringResource(R.string.module_files_sheet_property_shared_by),
                value = item.ownerDeviceLabel,
            )
            DetailRow(
                label = stringResource(R.string.module_files_sheet_property_shared_at),
                value = DateUtils.getRelativeTimeSpanString(
                    item.sharedFile.sharedAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
            )
            DetailRow(
                label = stringResource(R.string.module_files_sheet_property_expires_at),
                value = DateUtils.getRelativeTimeSpanString(
                    item.sharedFile.expiresAt.toEpochMilliseconds(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
            )
            if (item.sharedFile.availableOn.isNotEmpty()) {
                DetailRow(
                    label = stringResource(R.string.module_files_sheet_property_available_on),
                    value = item.sharedFile.availableOn.joinToString(", "),
                )
            }

            Spacer(modifier = Modifier.size(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val openEnabled = item.canOpenOrSave && !item.isInFlight && !item.isOpenPreparing && !item.isDeleting
                OutlinedButton(
                    enabled = openEnabled,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                ) {
                    if (item.isOpenPreparing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.module_files_open_preparing))
                    } else {
                        Icon(Icons.AutoMirrored.TwoTone.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.module_files_action_open))
                    }
                }
                val saveEnabled = item.canOpenOrSave && !item.isInFlight && !item.isDeleting
                OutlinedButton(
                    enabled = saveEnabled,
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.TwoTone.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.module_files_action_save_as))
                }
            }

            if (item.isOwn && !item.isPendingDelete) {
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(
                    enabled = !item.isDeleting,
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (item.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.TwoTone.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.module_files_delete_action))
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.module_files_sheet_dismiss_action))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
