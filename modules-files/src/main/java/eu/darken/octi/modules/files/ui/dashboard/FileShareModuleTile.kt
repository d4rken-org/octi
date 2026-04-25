package eu.darken.octi.modules.files.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.files.core.FileShareInfo
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Composable
fun FileShareModuleTile(
    state: FileShareDashState,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    onTileClicked: () -> Unit = {},
    onUploadClicked: () -> Unit = {},
    onDownloadClicked: () -> Unit = {},
) {
    val nonExpiredFiles = state.info.files.filter { it.expiresAt > Clock.System.now() }
    val latestFile = nonExpiredFiles.maxByOrNull { it.sharedAt }
    // "Latest downloadable" mirrors `FileShareListVM`'s `canOpenOrSave`: a file must be both
    // listed in `availableOn` AND have a matching `connectorRefs` entry on a configured
    // connector. Diverges from `latestFile` only when the newest file is mid-mirror.
    val latestDownloadable = nonExpiredFiles
        .filter { f ->
            f.availableOn.any { idStr ->
                idStr in state.configuredConnectorIds && f.connectorRefs.containsKey(idStr)
            }
        }
        .maxByOrNull { it.sharedAt }

    Surface(
        modifier = modifier,
        onClick = onTileClicked,
        shape = RoundedCornerShape(12.dp),
        color = if (isWide) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.TwoTone.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.module_files_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                when {
                    state.isOurDevice && state.isSharingAvailable -> IconButton(
                        onClick = onUploadClicked,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Add,
                            contentDescription = stringResource(R.string.module_files_share_action),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    !state.isOurDevice && latestDownloadable != null -> IconButton(
                        onClick = onDownloadClicked,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.SaveAlt,
                            contentDescription = stringResource(R.string.module_files_save_action),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (nonExpiredFiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.module_files_tile_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.module_files_tile_count, nonExpiredFiles.size),
                    style = MaterialTheme.typography.bodySmall,
                )
                latestFile?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview2
@Composable
private fun FileShareModuleTilePreview() = PreviewWrapper {
    FileShareModuleTile(
        state = FileShareDashState(
            info = FileShareInfo(
                files = listOf(
                    FileShareInfo.SharedFile(
                        name = "document.pdf",
                        mimeType = "application/pdf",
                        size = 1024 * 1024,
                        blobKey = "test-key",
                        checksum = "abc123",
                        sharedAt = Clock.System.now(),
                        expiresAt = Clock.System.now() + 48.hours,
                        availableOn = setOf("connector-1"),
                    ),
                ),
            ),
            isOurDevice = true,
            isSharingAvailable = true,
            configuredConnectorIds = setOf("connector-1"),
        ),
    )
}

@Preview2
@Composable
private fun FileShareModuleTileEmptyPreview() = PreviewWrapper {
    FileShareModuleTile(
        state = FileShareDashState(
            info = FileShareInfo(),
            isOurDevice = false,
            isSharingAvailable = false,
            configuredConnectorIds = emptySet(),
        ),
    )
}
