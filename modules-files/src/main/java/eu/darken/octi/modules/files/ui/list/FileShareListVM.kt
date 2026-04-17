package eu.darken.octi.modules.files.ui.list

import android.net.Uri
import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
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
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobChecksumMismatchException
import eu.darken.octi.sync.core.blob.BlobManager
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
) : ViewModel4(dispatcherProvider) {

    data class State(
        val deviceLabel: String = "",
        val isOurDevice: Boolean = false,
        val quotaItems: List<QuotaItem> = emptyList(),
        val files: List<FileItem> = emptyList(),
    )

    data class QuotaItem(
        val label: String,
        val usedBytes: Long,
        val totalBytes: Long,
    )

    data class FileItem(
        val sharedFile: FileShareInfo.SharedFile,
        val isAvailable: Boolean,
    )

    sealed interface UiEvent {
        data class ShowMessage(@StringRes val messageRes: Int) : UiEvent
    }

    val uiEvents = SingleEventFlow<UiEvent>()
    private val deviceIdFlow = MutableStateFlow<DeviceId?>(null)

    val state: Flow<State> = deviceIdFlow
        .filterNotNull()
        .flatMapLatest { targetDeviceId ->
            combine(
                fileShareRepo.state,
                moduleManager.byDevice,
                blobManager.quotas(),
            ) { repoState, byDevice, quotas ->
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
                        FileItem(
                            sharedFile = sharedFile,
                            isAvailable = isOurDevice || sharedFile.availableOn.any { it in configuredConnectorIds },
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

                State(
                    deviceLabel = deviceLabel,
                    isOurDevice = isOurDevice,
                    quotaItems = quotaItems,
                    files = files,
                )
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
