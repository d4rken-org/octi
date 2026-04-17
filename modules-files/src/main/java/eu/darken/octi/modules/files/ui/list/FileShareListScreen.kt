package eu.darken.octi.modules.files.ui.list

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.common.R as CommonR
import androidx.compose.foundation.layout.fillMaxWidth
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Composable
fun FileShareListScreenHost(
    deviceId: String,
    vm: FileShareListVM = hiltViewModel(),
) {
    LaunchedEffect(deviceId) { vm.initialize(deviceId) }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm.uiEvents) {
        vm.uiEvents.collect { event ->
            when (event) {
                is FileShareListVM.UiEvent.ShowMessage -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { vm.onShareFile(it) }
    }

    var pendingSave by remember { mutableStateOf<FileShareInfo.SharedFile?>(null) }
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val target = pendingSave ?: return@rememberLauncherForActivityResult
        pendingSave = null
        result.data?.data?.let { vm.onSaveFile(target, it) }
    }

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        FileShareListScreen(
            state = it,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onShareClick = { openDocLauncher.launch(arrayOf("*/*")) },
            onSaveClick = { file ->
                pendingSave = file
                createDocLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = file.mimeType.ifBlank { "application/octet-stream" }
                        putExtra(Intent.EXTRA_TITLE, file.name)
                    }
                )
            },
            onDeleteClick = { file -> vm.onDeleteFile(file) },
        )
    }
}

@Composable
fun FileShareListScreen(
    state: FileShareListVM.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onSaveClick: (FileShareInfo.SharedFile) -> Unit = {},
    onDeleteClick: (FileShareInfo.SharedFile) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.module_files_list_title))
                        if (state.deviceLabel.isNotEmpty()) {
                            Text(
                                text = stringResource(CommonR.string.device_x_label, state.deviceLabel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isOurDevice && state.isSharingAvailable) {
                FloatingActionButton(onClick = onShareClick) {
                    Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.module_files_share_action))
                }
            }
        },
    ) { padding ->
        if (state.files.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.isOurDevice && state.quotaItems.isNotEmpty()) {
                    QuotaSummary(state = state)
                }
                state.uploadProgress?.let { UploadProgressBar(it) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    if (state.isOurDevice && !state.isSharingAvailable) {
                        Text(
                            text = stringResource(R.string.module_files_unavailable_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.module_files_unavailable_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.module_files_tile_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.isOurDevice && state.quotaItems.isNotEmpty()) {
                    item {
                        QuotaSummary(state = state)
                    }
                }
                state.uploadProgress?.let { progress ->
                    item { UploadProgressBar(progress) }
                }
                items(
                    items = state.files,
                    key = { it.sharedFile.blobKey },
                ) { item ->
                    FileItemRow(
                        item = item,
                        isOurDevice = state.isOurDevice,
                        onSaveClick = { onSaveClick(item.sharedFile) },
                        onDeleteClick = { onDeleteClick(item.sharedFile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadProgressBar(progress: BlobProgress) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.module_files_uploading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QuotaSummary(state: FileShareListVM.State) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.module_files_quota_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.quotaItems.forEach { quota ->
            Text(
                text = stringResource(
                    R.string.module_files_quota_item,
                    quota.label,
                    Formatter.formatFileSize(context, quota.usedBytes),
                    Formatter.formatFileSize(context, quota.totalBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FileItemRow(
    item: FileShareListVM.FileItem,
    isOurDevice: Boolean,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val isDownloading = item.downloadProgress != null
    Column(
        modifier = Modifier
            .clickable(enabled = !isOurDevice && item.isAvailable && !isDownloading) { onSaveClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sharedFile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(Formatter.formatFileSize(context, item.sharedFile.size))
                        if (!item.isAvailable) {
                            append(" \u2022 ")
                            append(context.getString(R.string.module_files_unavailable))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.isAvailable && !isOurDevice && !isDownloading) {
                IconButton(onClick = onSaveClick) {
                    Icon(Icons.TwoTone.SaveAlt, contentDescription = stringResource(R.string.module_files_save_action))
                }
            }
            if (isOurDevice) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.module_files_delete_action))
                }
            }
        }
        item.downloadProgress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Preview2
@Composable
private fun FileShareListScreenPreview() = PreviewWrapper {
    FileShareListScreen(
        state = FileShareListVM.State(
            deviceLabel = "Pixel 8",
            isOurDevice = true,
            quotaItems = listOf(
                FileShareListVM.QuotaItem(
                    label = "octi.example.com",
                    usedBytes = 12 * 1024 * 1024,
                    totalBytes = 25 * 1024 * 1024,
                )
            ),
            files = listOf(
                FileShareListVM.FileItem(
                    sharedFile = FileShareInfo.SharedFile(
                        name = "document.pdf",
                        mimeType = "application/pdf",
                        size = 2 * 1024 * 1024,
                        blobKey = "key-1",
                        checksum = "abc",
                        sharedAt = Clock.System.now(),
                        expiresAt = Clock.System.now() + 48.hours,
                        availableOn = setOf("conn-1"),
                    ),
                    isAvailable = true,
                ),
                FileShareListVM.FileItem(
                    sharedFile = FileShareInfo.SharedFile(
                        name = "photo.jpg",
                        mimeType = "image/jpeg",
                        size = 512 * 1024,
                        blobKey = "key-2",
                        checksum = "def",
                        sharedAt = Clock.System.now(),
                        expiresAt = Clock.System.now() + 24.hours,
                        availableOn = setOf("conn-1", "conn-2"),
                    ),
                    isAvailable = true,
                ),
            ),
        ),
    )
}

@Preview2
@Composable
private fun FileShareListScreenEmptyPreview() = PreviewWrapper {
    FileShareListScreen(
        state = FileShareListVM.State(deviceLabel = "Pixel 8", isOurDevice = false),
    )
}
