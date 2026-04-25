package eu.darken.octi.modules.files.ui.list

import android.net.Uri
import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.module.core.BaseModuleRepo
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.files.core.FileKey
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareService
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.files.core.PendingDelete
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobChecksumMismatchException
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import eu.darken.octi.sync.core.blob.RetryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.time.Clock

@HiltViewModel
class FileShareListVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val fileShareRepo: FileShareRepo,
    private val fileShareService: FileShareService,
    private val moduleManager: ModuleManager,
    private val blobManager: BlobManager,
    private val syncSettings: SyncSettings,
    private val fileShareSettings: FileShareSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val files: List<FileItem> = emptyList(),
        val availableDevices: List<DeviceOption> = emptyList(),
        val activeFilters: Set<DeviceId> = emptySet(),
        val sortBy: SortKey = SortKey.DATE,
        val sortDescending: Boolean = true,
        val activeUpload: ActiveUpload? = null,
        val quotaItems: List<QuotaItem> = emptyList(),
        val isSharingAvailable: Boolean = true,
        val isUsageHintDismissed: Boolean = false,
        val sheetTargetKey: FileKey? = null,
    ) {
        val sheetTarget: FileItem? get() = files.firstOrNull { it.key == sheetTargetKey }
    }

    /** Stable identity for a row. `blobKey` alone could in theory collide across owners. */
    data class FileItem(
        val key: FileKey,
        val sharedFile: FileShareInfo.SharedFile,
        val ownerDeviceId: DeviceId,
        val ownerDeviceLabel: String,
        val isOwn: Boolean,
        val canOpenOrSave: Boolean,
        val isDeleting: Boolean = false,
        val isPendingDelete: Boolean = false,
        val isOpenPreparing: Boolean = false,
        val downloadProgress: BlobProgress? = null,
        val uploadProgress: BlobProgress? = null,
        val missingConnectors: List<MissingConnector> = emptyList(),
    ) {
        val isInFlight: Boolean get() = downloadProgress != null || uploadProgress != null
        val anyRetrying: Boolean get() = missingConnectors.any { it.status is RetryStatus.RetryingAt }
        val canRetry: Boolean get() = missingConnectors.any {
            it.status is RetryStatus.Stopped || it.status is RetryStatus.QuotaExceeded
        }
    }

    /** Top-level in-flight share — exposed before a [FileShareInfo.SharedFile] row exists. */
    data class ActiveUpload(
        val blobKey: String,
        val fileName: String,
        val progress: BlobProgress,
    )

    data class DeviceOption(
        val deviceId: DeviceId,
        val label: String,
        val isOwn: Boolean,
    )

    data class QuotaItem(
        val label: String,
        val usedBytes: Long,
        val totalBytes: Long,
    )

    data class MissingConnector(
        val connectorId: ConnectorId,
        val label: String,
        val status: RetryStatus,
    )

    enum class SortKey { DATE, NAME, SIZE, DEVICE }

    sealed interface UiEvent {
        data class ShowMessage(@StringRes val messageRes: Int) : UiEvent
        data class ShowMessageWithSize(@StringRes val messageRes: Int, val bytes: Long) : UiEvent
        data class OpenFile(val uri: Uri, val mimeType: String) : UiEvent
    }

    val uiEvents = SingleEventFlow<UiEvent>()

    private val _filters = MutableStateFlow<Set<DeviceId>>(emptySet())
    private val _sortBy = MutableStateFlow(SortKey.DATE)
    private val _sortDescending = MutableStateFlow(true)
    private val _deleting = MutableStateFlow<Set<FileKey>>(emptySet())
    private val _openPreparing = MutableStateFlow<Set<FileKey>>(emptySet())
    private val _sheetTarget = MutableStateFlow<FileKey?>(null)

    private data class RepoSnapshot(
        val repoState: BaseModuleRepo.State<FileShareInfo>,
        val moduleEnabled: Boolean,
        val byDevice: ModuleManager.ByDevice,
    )

    private data class TransferSnapshot(
        val transfers: Map<String, FileShareService.Transfer>,
        val retryStatus: Map<Pair<ConnectorId, BlobKey>, RetryStatus>,
        val quotas: Map<ConnectorId, BlobStoreQuota?>,
    )

    private data class UiSnapshot(
        val filters: Set<DeviceId>,
        val sortBy: SortKey,
        val sortDescending: Boolean,
        val deleting: Set<FileKey>,
        val openPreparing: Set<FileKey>,
        val sheetTarget: FileKey?,
    )

    private data class SettingsSnapshot(
        val pendingDeletes: Map<String, PendingDelete>,
        val isUsageHintDismissed: Boolean,
    )

    val state: Flow<State> = combine(
        combine(
            fileShareRepo.state,
            fileShareRepo.isEnabled,
            moduleManager.byDevice,
        ) { state, enabled, byDevice -> RepoSnapshot(state, enabled, byDevice) },
        combine(
            fileShareService.transfers,
            blobManager.retryStatus,
            blobManager.quotas(),
        ) { transfers, retry, quotas -> TransferSnapshot(transfers, retry, quotas) },
        combine(
            _filters,
            combine(_sortBy, _sortDescending) { by, desc -> by to desc },
            _deleting,
            _openPreparing,
            _sheetTarget,
        ) { filters, sort, deleting, opening, sheet ->
            UiSnapshot(filters, sort.first, sort.second, deleting, opening, sheet)
        },
        combine(
            fileShareSettings.pendingDeletes.flow,
            fileShareSettings.isUsageHintDismissed.flow,
        ) { pendingDeletes, hint -> SettingsSnapshot(pendingDeletes, hint) },
    ) { repo, transfer, ui, settings ->
        buildState(repo, transfer, ui, settings)
    }
        .setupCommonEventHandlers(TAG) { "state" }
        .shareLatest(vmScope)

    private fun buildState(
        repo: RepoSnapshot,
        transfer: TransferSnapshot,
        ui: UiSnapshot,
        settings: SettingsSnapshot,
    ): State {
        val ownDeviceId = syncSettings.deviceId
        val configuredById = transfer.quotas.keys.associateBy { it.idString }

        val deviceData: List<Pair<DeviceId, FileShareInfo>> = buildList {
            repo.repoState.self?.let { add(it.deviceId to it.data) }
            repo.repoState.others.forEach { add(it.deviceId to it.data) }
        }

        val labelByDevice: Map<DeviceId, String> = deviceData
            .associate { (id, _) -> id to deviceLabelFor(id, repo.byDevice) }

        val availableDevices = deviceData
            .map { (id, _) ->
                DeviceOption(
                    deviceId = id,
                    label = labelByDevice[id] ?: id.id.take(8),
                    isOwn = id == ownDeviceId,
                )
            }
            .distinctBy { it.deviceId }
            .sortedWith(compareByDescending<DeviceOption> { it.isOwn }.thenBy { it.label })

        val now = Clock.System.now()
        val filterIsEmpty = ui.filters.isEmpty()

        val items: List<FileItem> = deviceData.flatMap { (ownerId, info) ->
            if (!filterIsEmpty && ownerId !in ui.filters) return@flatMap emptyList()
            val ownerLabel = labelByDevice[ownerId] ?: ownerId.id.take(8)
            val isOwn = ownerId == ownDeviceId
            info.files
                .filter { now <= it.expiresAt }
                .map { sharedFile ->
                    val key = FileKey.of(ownerId, sharedFile.blobKey)
                    val transferEntry = transfer.transfers[sharedFile.blobKey]
                    val download = transferEntry
                        ?.takeIf { it.direction == FileShareService.Transfer.Direction.DOWNLOAD }
                        ?.progress
                    val upload = transferEntry
                        ?.takeIf { it.direction == FileShareService.Transfer.Direction.UPLOAD }
                        ?.progress
                    val candidates = sharedFile.availableOn.mapNotNull { idStr ->
                        val cId = configuredById[idStr] ?: return@mapNotNull null
                        val ref = sharedFile.connectorRefs[idStr] ?: return@mapNotNull null
                        cId to ref
                    }
                    FileItem(
                        key = key,
                        sharedFile = sharedFile,
                        ownerDeviceId = ownerId,
                        ownerDeviceLabel = ownerLabel,
                        isOwn = isOwn,
                        canOpenOrSave = candidates.isNotEmpty(),
                        isDeleting = key in ui.deleting,
                        isPendingDelete = sharedFile.blobKey in settings.pendingDeletes,
                        isOpenPreparing = key in ui.openPreparing,
                        downloadProgress = download,
                        uploadProgress = upload,
                        missingConnectors = if (isOwn) {
                            buildMissingConnectors(sharedFile, transfer.quotas.keys, transfer.retryStatus)
                        } else emptyList(),
                    )
                }
        }

        val sortedItems = items.sortedWith(comparatorFor(ui.sortBy, ui.sortDescending))

        val quotaItems = transfer.quotas.values
            .filterNotNull()
            .map { QuotaItem(label = it.connectorId.subtype, usedBytes = it.usedBytes, totalBytes = it.totalBytes) }
            .sortedBy { it.label }

        val activeUpload = transfer.transfers.values
            .firstOrNull { it.direction == FileShareService.Transfer.Direction.UPLOAD }
            ?.let { ActiveUpload(blobKey = it.blobKey, fileName = it.fileName, progress = it.progress) }

        return State(
            files = sortedItems,
            availableDevices = availableDevices,
            activeFilters = ui.filters,
            sortBy = ui.sortBy,
            sortDescending = ui.sortDescending,
            activeUpload = activeUpload,
            quotaItems = quotaItems,
            isSharingAvailable = repo.moduleEnabled && configuredById.isNotEmpty(),
            isUsageHintDismissed = settings.isUsageHintDismissed,
            sheetTargetKey = ui.sheetTarget,
        )
    }

    private fun comparatorFor(by: SortKey, descending: Boolean): Comparator<FileItem> {
        val base: Comparator<FileItem> = when (by) {
            SortKey.DATE -> compareBy { it.sharedFile.sharedAt }
            SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.sharedFile.name }
            SortKey.SIZE -> compareBy { it.sharedFile.size }
            SortKey.DEVICE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.ownerDeviceLabel }
        }
        return if (descending) base.reversed() else base
    }

    @Suppress("UNCHECKED_CAST")
    private fun deviceLabelFor(deviceId: DeviceId, byDevice: ModuleManager.ByDevice): String {
        val meta = byDevice.devices[deviceId]
            ?.firstOrNull { it.data is MetaInfo } as? ModuleData<MetaInfo>
        return meta?.data?.labelOrFallback ?: deviceId.id.take(8)
    }

    private fun buildMissingConnectors(
        sharedFile: FileShareInfo.SharedFile,
        configuredConnectors: Set<ConnectorId>,
        retryStatus: Map<Pair<ConnectorId, BlobKey>, RetryStatus>,
    ): List<MissingConnector> {
        val blobKey = BlobKey(sharedFile.blobKey)
        return configuredConnectors
            .filter { it.idString !in sharedFile.availableOn }
            .map { connectorId ->
                MissingConnector(
                    connectorId = connectorId,
                    label = connectorId.subtype,
                    status = retryStatus[connectorId to blobKey] ?: RetryStatus.RetryingAt(
                        nextAttemptAt = Clock.System.now(),
                        failureCount = 0,
                    ),
                )
            }
            .sortedBy { it.label }
    }

    /** Idempotent — only seeds the initial filter once on cold open. Later filter edits win. */
    fun initialize(initialDeviceFilter: String?) {
        if (initialDeviceFilter.isNullOrBlank()) return
        if (_filters.value.isNotEmpty()) return
        _filters.value = setOf(DeviceId(initialDeviceFilter))
    }

    fun onShareFile(uri: Uri) = launch {
        when (val result = fileShareService.shareFile(uri)) {
            is FileShareService.ShareResult.Success ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_upload_success))
            is FileShareService.ShareResult.PartialMirror ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_upload_partial))
            is FileShareService.ShareResult.AllConnectorsFailed ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_upload_failed))
            is FileShareService.ShareResult.FileTooLarge ->
                uiEvents.tryEmit(UiEvent.ShowMessageWithSize(R.string.module_files_upload_too_large, result.maxBytes))
            is FileShareService.ShareResult.NoEligibleConnectors ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_upload_no_connectors))
            is FileShareService.ShareResult.Cancelled ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_upload_cancelled))
        }
    }

    fun onSaveFile(item: FileItem, uri: Uri) = launch {
        when (val result = fileShareService.saveFile(item.sharedFile, item.ownerDeviceId, uri)) {
            is FileShareService.SaveResult.Success ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_save_success))
            is FileShareService.SaveResult.NotAvailable ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_save_not_available))
            is FileShareService.SaveResult.Failed -> {
                val msg = if (result.cause is BlobChecksumMismatchException) {
                    R.string.module_files_checksum_mismatch
                } else {
                    R.string.module_files_save_failed
                }
                uiEvents.tryEmit(UiEvent.ShowMessage(msg))
            }
            is FileShareService.SaveResult.Cancelled ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_save_cancelled))
        }
    }

    fun onOpenFile(item: FileItem) = launch {
        _openPreparing.update { it + item.key }
        try {
            when (val result = fileShareService.openFile(item.sharedFile, item.ownerDeviceId)) {
                is FileShareService.OpenResult.Success ->
                    uiEvents.tryEmit(UiEvent.OpenFile(result.contentUri, result.mimeType))
                is FileShareService.OpenResult.NotAvailable ->
                    uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_save_not_available))
                is FileShareService.OpenResult.Failed -> {
                    val msg = if (result.cause is BlobChecksumMismatchException) {
                        R.string.module_files_checksum_mismatch
                    } else {
                        R.string.module_files_open_failed
                    }
                    uiEvents.tryEmit(UiEvent.ShowMessage(msg))
                }
                is FileShareService.OpenResult.Cancelled -> Unit
            }
        } finally {
            _openPreparing.update { it - item.key }
        }
    }

    fun onRetryFile(item: FileItem) = launch {
        fileShareService.retryMirror(item.sharedFile.blobKey)
    }

    fun onCancelTransfer(blobKey: String) {
        fileShareService.cancelTransfer(blobKey)
    }

    fun onDismissUsageHint() = launch {
        fileShareSettings.isUsageHintDismissed.value(true)
    }

    fun onDeleteFile(item: FileItem) = launch {
        _deleting.update { it + item.key }
        try {
            val result = runCatching { fileShareService.deleteOwnFile(item.sharedFile.blobKey) }
            val message: Int = when (val r = result.getOrNull()) {
                FileShareService.DeleteResult.Deleted -> R.string.module_files_delete_success
                FileShareService.DeleteResult.NotFound -> R.string.module_files_delete_failed
                is FileShareService.DeleteResult.Partial -> R.string.module_files_delete_partial
                null -> R.string.module_files_delete_failed
            }
            uiEvents.tryEmit(UiEvent.ShowMessage(message))
        } finally {
            _deleting.update { it - item.key }
        }
    }

    fun onToggleFilter(deviceId: DeviceId) {
        _filters.update { current ->
            if (deviceId in current) current - deviceId else current + deviceId
        }
    }

    fun onClearFilter(deviceId: DeviceId) {
        _filters.update { it - deviceId }
    }

    fun onSortChange(key: SortKey) {
        if (_sortBy.value == key) {
            _sortDescending.update { !it }
        } else {
            _sortBy.value = key
            _sortDescending.value = true
        }
    }

    fun onRowClick(item: FileItem) {
        _sheetTarget.value = item.key
    }

    fun onSheetDismiss() {
        _sheetTarget.value = null
    }

    companion object {
        private val TAG = logTag("Module", "Files", "List", "VM")
    }
}
