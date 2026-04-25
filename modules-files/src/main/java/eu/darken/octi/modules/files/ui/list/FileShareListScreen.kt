package eu.darken.octi.modules.files.ui.list

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.ArrowDownward
import androidx.compose.material.icons.twotone.ArrowUpward
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.files.core.FileKey
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.RetryStatus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private val TAG = logTag("Module", "Files", "List", "Screen")

@Composable
fun FileShareListScreenHost(
    initialDeviceFilter: String?,
    vm: FileShareListVM = hiltViewModel(),
) {
    LaunchedEffect(initialDeviceFilter) { vm.initialize(initialDeviceFilter) }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm.uiEvents) {
        vm.uiEvents.collect { event ->
            when (event) {
                is FileShareListVM.UiEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is FileShareListVM.UiEvent.ShowMessageWithSize ->
                    snackbarHostState.showSnackbar(
                        context.getString(event.messageRes, Formatter.formatFileSize(context, event.bytes))
                    )
                is FileShareListVM.UiEvent.OpenFile -> {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(event.uri, event.mimeType.ifBlank { "application/octet-stream" })
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri(null, event.uri)
                    }
                    try {
                        context.startActivity(
                            Intent.createChooser(viewIntent, context.getString(R.string.module_files_open_chooser))
                        )
                    } catch (_: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(context.getString(R.string.module_files_open_no_app))
                    } catch (e: SecurityException) {
                        log(TAG, ERROR) { "open dispatch failed: ${e.asLog()}" }
                        snackbarHostState.showSnackbar(context.getString(R.string.module_files_open_failed))
                    }
                }
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { vm.onShareFile(it) }
    }

    var pendingSave by remember { mutableStateOf<FileShareListVM.FileItem?>(null) }
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val target = pendingSave ?: return@rememberLauncherForActivityResult
        pendingSave = null
        result.data?.data?.let { vm.onSaveFile(target, it) }
    }

    val state by vm.state.collectAsState(initial = null)

    state?.let { current ->
        FileShareListScreen(
            state = current,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onShareClick = { openDocLauncher.launch(arrayOf("*/*")) },
            onRowClick = { item -> vm.onRowClick(item) },
            onRowSecondaryClick = { item ->
                if (item.isOwn) vm.onDeleteFile(item)
                else launchSaveAs(createDocLauncher, item) { pendingSave = it }
            },
            onRetryClick = { item -> vm.onRetryFile(item) },
            onCancelClick = { blobKey -> vm.onCancelTransfer(blobKey) },
            onAddFilter = { deviceId -> vm.onToggleFilter(deviceId) },
            onRemoveFilter = { deviceId -> vm.onClearFilter(deviceId) },
            onSortChange = { key -> vm.onSortChange(key) },
            onSyncClick = { vm.onSyncNow() },
            onDismissHint = { vm.onDismissUsageHint() },
            onSheetDismiss = { vm.onSheetDismiss() },
            onSheetOpen = { item -> vm.onOpenFile(item) },
            onSheetSave = { item -> launchSaveAs(createDocLauncher, item) { pendingSave = it } },
            onSheetDelete = { item -> vm.onDeleteFile(item) },
        )
    }
}

private fun launchSaveAs(
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>,
    item: FileShareListVM.FileItem,
    setPending: (FileShareListVM.FileItem) -> Unit,
) {
    setPending(item)
    launcher.launch(
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = item.sharedFile.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, item.sharedFile.name)
        }
    )
}

@Composable
fun FileShareListScreen(
    state: FileShareListVM.State,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onRowClick: (FileShareListVM.FileItem) -> Unit = {},
    onRowSecondaryClick: (FileShareListVM.FileItem) -> Unit = {},
    onRetryClick: (FileShareListVM.FileItem) -> Unit = {},
    onCancelClick: (String) -> Unit = {},
    onAddFilter: (DeviceId) -> Unit = {},
    onRemoveFilter: (DeviceId) -> Unit = {},
    onSortChange: (FileShareListVM.SortKey) -> Unit = {},
    onSyncClick: () -> Unit = {},
    onDismissHint: () -> Unit = {},
    onSheetDismiss: () -> Unit = {},
    onSheetOpen: (FileShareListVM.FileItem) -> Unit = {},
    onSheetSave: (FileShareListVM.FileItem) -> Unit = {},
    onSheetDelete: (FileShareListVM.FileItem) -> Unit = {},
) {
    val showUsageHint = !state.isUsageHintDismissed
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_files_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, null)
                    }
                },
                actions = {
                    SyncAction(state = state, onClick = onSyncClick)
                    SortMenu(state = state, onSortChange = onSortChange)
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.isSharingAvailable) {
                FloatingActionButton(onClick = onShareClick) {
                    Icon(Icons.TwoTone.Add, contentDescription = stringResource(R.string.module_files_share_action))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.availableDevices.size > 1) {
                FilterRow(
                    state = state,
                    onAddFilter = onAddFilter,
                    onRemoveFilter = onRemoveFilter,
                )
            }
            if (state.files.isEmpty() && state.activeUpload == null) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showUsageHint) UsageHintCard(onDismiss = onDismissHint)
                    if (state.quotaItems.isNotEmpty()) QuotaSummary(state = state)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        if (!state.isSharingAvailable) {
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (showUsageHint) item { UsageHintCard(onDismiss = onDismissHint) }
                    if (state.quotaItems.isNotEmpty()) item { QuotaSummary(state = state) }
                    state.activeUpload?.let { upload ->
                        item {
                            UploadProgressRow(
                                upload = upload,
                                onCancel = { onCancelClick(upload.blobKey) },
                            )
                        }
                    }
                    items(items = state.files, key = { it.key.raw }) { item ->
                        FileItemRow(
                            item = item,
                            onRowClick = { onRowClick(item) },
                            onSecondaryClick = { onRowSecondaryClick(item) },
                            onRetryClick = { onRetryClick(item) },
                            onCancelClick = { onCancelClick(item.sharedFile.blobKey) },
                        )
                    }
                }
            }
        }

        state.sheetTarget?.let { target ->
            FileDetailBottomSheet(
                item = target,
                onDismiss = onSheetDismiss,
                onOpen = { onSheetOpen(target) },
                onSave = { onSheetSave(target) },
                onDelete = { onSheetDelete(target) },
            )
        }
    }
}

@Composable
private fun FilterRow(
    state: FileShareListVM.State,
    onAddFilter: (DeviceId) -> Unit,
    onRemoveFilter: (DeviceId) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.activeFilters.isEmpty()) {
            Text(
                text = stringResource(R.string.module_files_filter_all_devices),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.activeFilters.forEach { deviceId ->
                    val opt = state.availableDevices.firstOrNull { it.deviceId == deviceId }
                    val label = opt?.label ?: deviceId.id.take(8)
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveFilter(deviceId) },
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                Icons.TwoTone.Close,
                                contentDescription = stringResource(R.string.module_files_filter_remove_action),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { pickerOpen = true }) {
                Icon(
                    Icons.TwoTone.Add,
                    contentDescription = stringResource(R.string.module_files_filter_add_action),
                )
            }
            DropdownMenu(
                expanded = pickerOpen,
                onDismissRequest = { pickerOpen = false },
            ) {
                state.availableDevices.forEach { opt ->
                    val checked = opt.deviceId in state.activeFilters
                    DropdownMenuItem(
                        text = { Text(opt.label) },
                        onClick = { onAddFilter(opt.deviceId) },
                        leadingIcon = {
                            if (checked) Icon(Icons.TwoTone.Check, null)
                            else Spacer(modifier = Modifier.size(24.dp))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncAction(
    state: FileShareListVM.State,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = !state.isSyncingNow) {
        if (state.isSyncingNow) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                imageVector = Icons.TwoTone.Sync,
                contentDescription = stringResource(R.string.module_files_sync_now_action),
            )
        }
    }
}

@Composable
private fun SortMenu(
    state: FileShareListVM.State,
    onSortChange: (FileShareListVM.SortKey) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.TwoTone.Sort, contentDescription = stringResource(R.string.module_files_sort_action))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FileShareListVM.SortKey.entries.forEach { key ->
                val labelRes = when (key) {
                    FileShareListVM.SortKey.DATE -> R.string.module_files_sort_by_date
                    FileShareListVM.SortKey.NAME -> R.string.module_files_sort_by_name
                    FileShareListVM.SortKey.SIZE -> R.string.module_files_sort_by_size
                    FileShareListVM.SortKey.DEVICE -> R.string.module_files_sort_by_device
                }
                val isActive = state.sortBy == key
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        onSortChange(key)
                        open = false
                    },
                    leadingIcon = if (isActive) {
                        {
                            Icon(
                                if (state.sortDescending) Icons.TwoTone.ArrowDownward
                                else Icons.TwoTone.ArrowUpward,
                                contentDescription = null,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun UsageHintCard(onDismiss: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.module_files_usage_hint_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(R.string.module_files_hint_dismiss_action),
                )
            }
        }
    }
}

@Composable
private fun UploadProgressRow(
    upload: FileShareListVM.ActiveUpload,
    onCancel: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                    text = upload.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.module_files_upload_progress,
                        "${(upload.progress.fraction * 100).toInt()}%"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.TwoTone.Close,
                    contentDescription = stringResource(R.string.module_files_cancel_action),
                )
            }
        }
        LinearProgressIndicator(
            progress = { upload.progress.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun QuotaSummary(state: FileShareListVM.State) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
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
}

@Composable
private fun FileItemRow(
    item: FileShareListVM.FileItem,
    onRowClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    onRetryClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    val context = LocalContext.current
    val rowEnabled = !item.isInFlight && !item.isDeleting
    Column(
        modifier = Modifier.clickable(enabled = rowEnabled, onClick = onRowClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(Formatter.formatFileSize(context, item.sharedFile.size))
                        append(" • ")
                        append(item.ownerDeviceLabel)
                        append(" • ")
                        append(
                            DateUtils.getRelativeTimeSpanString(
                                item.sharedFile.sharedAt.toEpochMilliseconds(),
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            )
                        )
                        if (item.isPendingDelete) {
                            append(" • ")
                            append(context.getString(R.string.module_files_pending_delete_label))
                        } else if (!item.canOpenOrSave && !item.isOwn) {
                            append(" • ")
                            append(context.getString(R.string.module_files_unavailable))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.isOwn) RetryStatusLine(item)
            }
            when {
                item.isInFlight -> {
                    IconButton(onClick = onCancelClick) {
                        Icon(
                            Icons.TwoTone.Close,
                            contentDescription = stringResource(R.string.module_files_cancel_action),
                        )
                    }
                }
                item.isDeleting -> {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                item.isOwn && item.canRetry -> {
                    IconButton(onClick = onRetryClick) {
                        Icon(
                            Icons.TwoTone.Refresh,
                            contentDescription = stringResource(R.string.module_files_retry_action),
                        )
                    }
                }
                item.isOwn && !item.isPendingDelete -> {
                    IconButton(onClick = onSecondaryClick) {
                        Icon(Icons.TwoTone.Delete, contentDescription = stringResource(R.string.module_files_delete_action))
                    }
                }
                !item.isOwn && item.canOpenOrSave -> {
                    IconButton(onClick = onSecondaryClick) {
                        Icon(Icons.TwoTone.SaveAlt, contentDescription = stringResource(R.string.module_files_save_action))
                    }
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
        item.uploadProgress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun RetryStatusLine(item: FileShareListVM.FileItem) {
    val context = LocalContext.current
    val terminal = item.missingConnectors.firstOrNull {
        it.status is RetryStatus.Stopped ||
            it.status is RetryStatus.FileTooLarge ||
            it.status is RetryStatus.QuotaExceeded
    }
    val text = when {
        terminal != null -> when (val s = terminal.status) {
            is RetryStatus.FileTooLarge ->
                context.getString(R.string.module_files_retry_status_file_too_large, terminal.label)
            is RetryStatus.QuotaExceeded ->
                context.getString(R.string.module_files_retry_status_quota, terminal.label)
            is RetryStatus.Stopped ->
                context.getString(R.string.module_files_retry_status_stopped)
            else -> null
        }
        item.anyRetrying -> context.getString(R.string.module_files_retry_status_retrying)
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Preview2
@Composable
private fun FileShareListScreenPreview() = PreviewWrapper {
    val device = DeviceId("self")
    val sample = FileShareInfo.SharedFile(
        name = "document.pdf",
        mimeType = "application/pdf",
        size = 2 * 1024 * 1024,
        blobKey = "key-1",
        checksum = "abc",
        sharedAt = Clock.System.now(),
        expiresAt = Clock.System.now() + 48.hours,
        availableOn = setOf("conn-1"),
    )
    FileShareListScreen(
        state = FileShareListVM.State(
            files = listOf(
                FileShareListVM.FileItem(
                    key = FileKey.of(device, sample.blobKey),
                    sharedFile = sample,
                    ownerDeviceId = device,
                    ownerDeviceLabel = "Pixel 8",
                    isOwn = true,
                    canOpenOrSave = true,
                ),
            ),
            availableDevices = listOf(
                FileShareListVM.DeviceOption(device, "Pixel 8", isOwn = true),
                FileShareListVM.DeviceOption(DeviceId("other"), "Galaxy S24", isOwn = false),
            ),
            quotaItems = listOf(
                FileShareListVM.QuotaItem("octi.example.com", 12 * 1024 * 1024, 25 * 1024 * 1024),
            ),
        ),
    )
}
