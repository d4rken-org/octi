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
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.files.R
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareService
import eu.darken.octi.modules.files.core.FileShareSettings
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobChecksumMismatchException
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
        val deviceLabel: String = "",
        val isOurDevice: Boolean = false,
        val isSharingAvailable: Boolean = true,
        val quotaItems: List<QuotaItem> = emptyList(),
        val files: List<FileItem> = emptyList(),
        val uploadProgress: BlobProgress? = null,
        val isUsageHintDismissed: Boolean = false,
    )

    data class QuotaItem(
        val label: String,
        val usedBytes: Long,
        val totalBytes: Long,
    )

    data class FileItem(
        val sharedFile: FileShareInfo.SharedFile,
        val isAvailable: Boolean,
        val downloadProgress: BlobProgress? = null,
    )

    sealed interface UiEvent {
        data class ShowMessage(@StringRes val messageRes: Int) : UiEvent
        data class ShowMessageWithSize(@StringRes val messageRes: Int, val bytes: Long) : UiEvent
    }

    val uiEvents = SingleEventFlow<UiEvent>()
    private val deviceIdFlow = MutableStateFlow<DeviceId?>(null)

    val state: Flow<State> = deviceIdFlow
        .filterNotNull()
        .flatMapLatest { targetDeviceId ->
            val baseFlow = combine(
                fileShareRepo.state,
                fileShareRepo.isEnabled,
                moduleManager.byDevice,
                blobManager.quotas(),
                fileShareService.transfers,
            ) { repoState, moduleEnabled, byDevice, quotas, transfers ->
                val isOurDevice = targetDeviceId == syncSettings.deviceId
                val configuredConnectorIds = quotas.keys.map { it.idString }.toSet()

                @Suppress("UNCHECKED_CAST")
                val metaInfo = byDevice.devices
                    .firstNotNullOfOrNull { (devId, modules) ->
                        if (devId == targetDeviceId) {
                            modules.firstOrNull { it.data is MetaInfo } as? ModuleData<MetaInfo>
                        } else null
                    }

                val deviceLabel = metaInfo?.data?.labelOrFallback ?: targetDeviceId.id.take(8)

                val fileShareData = if (isOurDevice) {
                    repoState.self?.data
                } else {
                    repoState.others.find { it.deviceId == targetDeviceId }?.data
                }

                val now = Clock.System.now()
                val files = (fileShareData?.files ?: emptyList())
                    .filter { now <= it.expiresAt }
                    .map { sharedFile ->
                        val download = transfers[sharedFile.blobKey]
                            ?.takeIf { it.direction == FileShareService.Transfer.Direction.DOWNLOAD }
                            ?.progress
                        FileItem(
                            sharedFile = sharedFile,
                            isAvailable = isOurDevice || sharedFile.availableOn.any { it in configuredConnectorIds },
                            downloadProgress = download,
                        )
                    }
                    .sortedByDescending { it.sharedFile.sharedAt }

                val quotaItems = if (isOurDevice) {
                    quotas.values
                        .filterNotNull()
                        .map {
                            QuotaItem(
                                label = it.connectorId.subtype,
                                usedBytes = it.usedBytes,
                                totalBytes = it.totalBytes,
                            )
                        }
                        .sortedBy { it.label }
                } else {
                    emptyList()
                }

                // In-flight uploads don't have a row in `files` yet; surface the first one so the
                // Share screen can show a top-level progress bar while the transfer runs.
                val uploadProgress = transfers.values
                    .firstOrNull { it.direction == FileShareService.Transfer.Direction.UPLOAD }
                    ?.progress

                State(
                    deviceLabel = deviceLabel,
                    isOurDevice = isOurDevice,
                    isSharingAvailable = moduleEnabled && configuredConnectorIds.isNotEmpty(),
                    quotaItems = quotaItems,
                    files = files,
                    uploadProgress = uploadProgress,
                )
            }
            combine(baseFlow, fileShareSettings.isUsageHintDismissed.flow) { base, hintDismissed ->
                base.copy(isUsageHintDismissed = hintDismissed)
            }
        }
        .setupCommonEventHandlers(TAG) { "state" }
        .shareLatest(vmScope)

    fun initialize(deviceIdStr: String) {
        deviceIdFlow.value = DeviceId(deviceIdStr)
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
        }
    }

    fun onSaveFile(sharedFile: FileShareInfo.SharedFile, uri: Uri) = launch {
        val ownerDeviceId = deviceIdFlow.value ?: return@launch
        when (val result = fileShareService.saveFile(sharedFile, ownerDeviceId, uri)) {
            is FileShareService.SaveResult.Success ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_save_success))
            is FileShareService.SaveResult.NotAvailable ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_save_not_available))
            is FileShareService.SaveResult.Failed -> {
                val message = if (result.cause is BlobChecksumMismatchException) {
                    R.string.module_files_checksum_mismatch
                } else {
                    R.string.module_files_save_failed
                }
                uiEvents.tryEmit(UiEvent.ShowMessage(message))
            }
        }
    }

    fun onDismissUsageHint() = launch {
        fileShareSettings.isUsageHintDismissed.value(true)
    }

    fun onDeleteFile(sharedFile: FileShareInfo.SharedFile) = launch {
        when (fileShareService.deleteOwnFile(sharedFile.blobKey)) {
            FileShareService.DeleteResult.Deleted ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_delete_success))

            FileShareService.DeleteResult.NotFound ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_delete_failed))

            is FileShareService.DeleteResult.Partial ->
                uiEvents.tryEmit(UiEvent.ShowMessage(R.string.module_files_delete_partial))
        }
    }

    companion object {
        private val TAG = logTag("Module", "Files", "List", "VM")
    }
}
