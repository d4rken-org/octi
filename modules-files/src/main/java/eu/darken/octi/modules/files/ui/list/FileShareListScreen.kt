package eu.darken.octi.modules.files.ui.list

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.InsertDriveFile
import androidx.compose.material.icons.automirrored.twotone.Sort
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.ArrowDownward
import androidx.compose.material.icons.twotone.ArrowUpward
import androidx.compose.material.icons.twotone.BugReport
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.files.core.FileKey
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.RetryStatus
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

private val TAG = logTag("Module", "Files", "List", "Screen")

@Composable
fun FileShareListScreenHost(
    initialDeviceFilter: String?,
    autoAction: Nav.Main.FileShareList.AutoAction? = null,
    incomingShareToken: String? = null,
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

    val state by vm.state.collectAsState(initial = null)

    // Cross-rotation flags for the dashboard-tile auto-action flow. Once the SAF picker is
    // launched, `autoLaunched` stays true so the LaunchedEffect doesn't double-fire on
    // recomposition. `autoFiringComplete` flips true after the result handler runs (or when the
    // download path gives up), guaranteeing any *subsequent* manual launcher event uses the
    // regular snackbar path. `pendingSaveKey` holds `FileKey.raw` so the live `FileItem` can be
    // resolved from the latest state when the result returns (snapshot at launch may be stale).
    var autoLaunched by rememberSaveable { mutableStateOf(false) }
    var autoFiringComplete by rememberSaveable { mutableStateOf(false) }
    var pendingSaveKey by rememberSaveable { mutableStateOf<String?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val auto = autoAction != null && autoLaunched && !autoFiringComplete
        if (uri != null) {
            if (auto) vm.enqueueShareFile(uri) else vm.onShareFile(uri)
        }
        if (auto) {
            autoFiringComplete = true
            vm.navUp()
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val auto = autoAction != null && autoLaunched && !autoFiringComplete
        val key = pendingSaveKey
        pendingSaveKey = null
        val uri = result.data?.data
        val target = key?.let { k -> state?.files?.firstOrNull { it.key.raw == k } }
        if (target != null && uri != null) {
            if (auto) vm.enqueueSaveFile(target, uri) else vm.onSaveFile(target, uri)
        } else if (uri != null && key != null) {
            // The row vanished between launching the picker and the user confirming —
            // remote deleted it, or expiry passed. The save is dropped; surface it to logs
            // so a real issue isn't silently swallowed (snackbar isn't reachable on the auto
            // path because we navUp immediately).
            log(TAG, WARN) { "save dropped: pending key=$key not in current state" }
        }
        if (auto) {
            autoFiringComplete = true
            vm.navUp()
        }
    }

    LaunchedEffect(autoAction) {
        if (autoAction == null || autoLaunched || autoFiringComplete) return@LaunchedEffect
        when (autoAction) {
            Nav.Main.FileShareList.AutoAction.UPLOAD -> {
                autoLaunched = true
                openDocLauncher.launch(arrayOf("*/*"))
            }
            Nav.Main.FileShareList.AutoAction.INCOMING_SHARE -> {
                autoLaunched = true
                if (incomingShareToken != null) vm.consumeIncomingShare(incomingShareToken)
                autoFiringComplete = true
            }
            Nav.Main.FileShareList.AutoAction.DOWNLOAD_LATEST -> {
                val target = withTimeoutOrNull(5_000) {
                    vm.state.mapNotNull { current ->
                        current.files
                            .filter {
                                !it.isOwn &&
                                    it.ownerDeviceId.id == initialDeviceFilter &&
                                    it.canOpenOrSave
                            }
                            .maxByOrNull { it.sharedFile.sharedAt }
                    }.first()
                }
                if (target != null) {
                    autoLaunched = true
                    pendingSaveKey = target.key.raw
                    createDocLauncher.launch(buildSaveAsIntent(target))
                } else {
                    // No eligible file in time — give up silently. User stays on the filtered
                    // list and can interact normally; result handlers still see auto=false
                    // because autoLaunched is still false.
                    autoFiringComplete = true
                }
            }
        }
    }

    val launchManualSave: (FileShareListVM.FileItem) -> Unit = { item ->
        pendingSaveKey = item.key.raw
        createDocLauncher.launch(buildSaveAsIntent(item))
    }

    state?.let { current ->
        FileShareListScreen(
            state = current,
            snackbarHostState = snackbarHostState,
            onNavigateUp = { vm.navUp() },
            onShareClick = { openDocLauncher.launch(arrayOf("*/*")) },
            onRowClick = { item -> vm.onRowClick(item) },
            onRowSecondaryClick = { item ->
                if (item.isOwn) vm.onDeleteFile(item)
                else launchManualSave(item)
            },
            onRetryClick = { item -> vm.onRetryFile(item) },
            onCancelClick = { blobKey -> vm.onCancelTransfer(blobKey) },
            onToggleFilter = { deviceId -> vm.onToggleFilter(deviceId) },
            onSortChange = { key -> vm.onSortChange(key) },
            onSyncClick = { vm.onSyncNow() },
            onDismissHint = { vm.onDismissUsageHint() },
            onSheetDismiss = { vm.onSheetDismiss() },
            onSheetOpen = { item -> vm.onOpenFile(item) },
            onSheetSave = { item -> launchManualSave(item) },
            onSheetDelete = { item -> vm.onDeleteFile(item) },
            onDebugShareTestUri = { variant ->
                val authority = "${BuildConfigWrap.APPLICATION_ID}.debug.testfixture"
                val path = when (variant) {
                    DebugTestUriVariant.UNSIZED -> "unsized"
                    DebugTestUriVariant.LYING_SIZE -> "lying-size"
                }
                vm.onShareFile(Uri.parse("content://$authority/$path"))
            },
        )
    }
}

enum class DebugTestUriVariant { UNSIZED, LYING_SIZE }

private fun buildSaveAsIntent(item: FileShareListVM.FileItem): Intent =
    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = item.sharedFile.mimeType.ifBlank { "application/octet-stream" }
        putExtra(Intent.EXTRA_TITLE, item.sharedFile.name)
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
    onToggleFilter: (DeviceId) -> Unit = {},
    onSortChange: (FileShareListVM.SortKey) -> Unit = {},
    onSyncClick: () -> Unit = {},
    onDismissHint: () -> Unit = {},
    onSheetDismiss: () -> Unit = {},
    onSheetOpen: (FileShareListVM.FileItem) -> Unit = {},
    onSheetSave: (FileShareListVM.FileItem) -> Unit = {},
    onSheetDelete: (FileShareListVM.FileItem) -> Unit = {},
    onDebugShareTestUri: (DebugTestUriVariant) -> Unit = {},
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
                    if (BuildConfigWrap.DEBUG) {
                        DebugTestUriMenu(onShareTestUri = onDebugShareTestUri)
                    }
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
                DevicesChipRow(
                    state = state,
                    onToggleFilter = onToggleFilter,
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

/**
 * Always-visible row of device chips. Empty `activeFilters` is the canonical "all selected"
 * form, so chips render selected when in the set OR when the set is empty. Tapping a chip
 * routes through [FileShareListVM.onToggleFilter] which canonicalizes the result back to
 * empty when appropriate (all selected or last one removed).
 */
@Composable
private fun DevicesChipRow(
    state: FileShareListVM.State,
    onToggleFilter: (DeviceId) -> Unit,
) {
    val allShown = state.activeFilters.isEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.module_files_filter_devices_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.availableDevices.forEach { opt ->
                val selected = allShown || opt.deviceId in state.activeFilters
                FilterChip(
                    selected = selected,
                    onClick = { onToggleFilter(opt.deviceId) },
                    label = { Text(opt.label) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.TwoTone.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                )
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
private fun DebugTestUriMenu(
    onShareTestUri: (DebugTestUriVariant) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.TwoTone.BugReport, contentDescription = "Debug test URIs")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Share Unsized URI (Tier B′)") },
                onClick = {
                    open = false
                    onShareTestUri(DebugTestUriVariant.UNSIZED)
                },
            )
            DropdownMenuItem(
                text = { Text("Share Lying-Size URI (Tier B → B′)") },
                onClick = {
                    open = false
                    onShareTestUri(DebugTestUriVariant.LYING_SIZE)
                },
            )
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
    // OctiServer rows disambiguate by accountLabel only when 2+ accounts share the same domain.
    // Single-account users get the clean domain row.
    val collidingOctiSubtypes = remember(state.quotaItems) {
        state.quotaItems
            .filter { it.connectorId.type == ConnectorType.OCTISERVER }
            .groupingBy { it.connectorId.subtype }
            .eachCount()
            .filterValues { it > 1 }
            .keys
    }
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
            state.quotaItems.forEach { status ->
                QuotaRow(status = status, collidingOctiSubtypes = collidingOctiSubtypes, context = context)
            }
        }
    }
}

@Composable
private fun QuotaRow(
    status: StorageStatus,
    collidingOctiSubtypes: Set<String>,
    context: android.content.Context,
) {
    val label = quotaConnectorLabel(status.connectorId, status.lastKnown?.accountLabel, collidingOctiSubtypes)
    val snapshot: StorageSnapshot? = status.lastKnown
    val almostFull = snapshot != null && snapshot.totalBytes > 0 &&
        snapshot.usedBytes.toDouble() / snapshot.totalBytes > QUOTA_WARN_THRESHOLD
    val isUnavailable = status is StorageStatus.Unavailable
    val color = when {
        almostFull -> MaterialTheme.colorScheme.error
        isUnavailable -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status is StorageStatus.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (isUnavailable) {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        val text = if (snapshot != null) {
            stringResource(
                R.string.module_files_quota_item,
                label,
                Formatter.formatFileSize(context, snapshot.usedBytes),
                Formatter.formatFileSize(context, snapshot.totalBytes),
            )
        } else {
            // Loading-from-cold or Unavailable with no last-known: show just the label so the
            // card row still anchors visually until data arrives.
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
private fun quotaConnectorLabel(
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

private const val QUOTA_WARN_THRESHOLD = 0.9

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
                        append(item.ownerDeviceLabel)
                        append(" • ")
                        append(Formatter.formatFileSize(context, item.sharedFile.size))
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            it.status is RetryStatus.QuotaExceeded ||
            it.status is RetryStatus.ServerStorageLow
    }
    val text = when {
        terminal != null -> when (terminal.status) {
            is RetryStatus.FileTooLarge ->
                context.getString(R.string.module_files_retry_status_file_too_large, terminal.label)
            is RetryStatus.QuotaExceeded ->
                context.getString(R.string.module_files_retry_status_quota, terminal.label)
            is RetryStatus.ServerStorageLow ->
                context.getString(R.string.module_files_retry_status_server_storage_low)
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
                StorageStatus.Ready(
                    connectorId = ConnectorId(ConnectorType.OCTISERVER, "octi.example.com", "acc-preview"),
                    snapshot = StorageSnapshot(
                        connectorId = ConnectorId(ConnectorType.OCTISERVER, "octi.example.com", "acc-preview"),
                        accountLabel = null,
                        usedBytes = 12L * 1024 * 1024,
                        totalBytes = 25L * 1024 * 1024,
                        availableBytes = 13L * 1024 * 1024,
                        maxFileBytes = 10L * 1024 * 1024,
                        perFileOverheadBytes = 1024,
                        updatedAt = Clock.System.now(),
                    ),
                ),
            ),
        ),
    )
}
