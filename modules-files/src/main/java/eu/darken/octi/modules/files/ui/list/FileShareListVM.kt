package eu.darken.octi.modules.files.ui.list

import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.files.core.FileShareInfo
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareService
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncSettings
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
    private val syncSettings: SyncSettings,
) : ViewModel4(dispatcherProvider) {

    data class State(
        val deviceLabel: String = "",
        val isOurDevice: Boolean = false,
        val files: List<FileItem> = emptyList(),
    )

    data class FileItem(
        val sharedFile: FileShareInfo.SharedFile,
        val isExpired: Boolean,
        val isAvailable: Boolean,
    )

    sealed interface UiEvent {
        data class ShowMessage(val message: String) : UiEvent
    }

    val uiEvents = SingleEventFlow<UiEvent>()
    private val deviceIdFlow = MutableStateFlow<DeviceId?>(null)

    val state: Flow<State> = deviceIdFlow
        .filterNotNull()
        .flatMapLatest { targetDeviceId ->
            combine(
                fileShareRepo.state,
                moduleManager.byDevice,
            ) { repoState, byDevice ->
                val isOurDevice = targetDeviceId == syncSettings.deviceId

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
                val files = (fileShareData?.files ?: emptyList()).map { sharedFile ->
                    FileItem(
                        sharedFile = sharedFile,
                        isExpired = now > sharedFile.expiresAt,
                        isAvailable = sharedFile.availableOn.isNotEmpty(),
                    )
                }.sortedByDescending { it.sharedFile.sharedAt }

                State(
                    deviceLabel = deviceLabel,
                    isOurDevice = isOurDevice,
                    files = files,
                )
            }
        }
        .setupCommonEventHandlers(TAG) { "state" }

    fun initialize(deviceIdStr: String) {
        deviceIdFlow.value = DeviceId(deviceIdStr)
    }

    fun onShareFile(uri: Uri) = launch {
        when (val result = fileShareService.shareFile(uri)) {
            is FileShareService.ShareResult.Success ->
                uiEvents.tryEmit(UiEvent.ShowMessage("File shared successfully"))
            is FileShareService.ShareResult.PartialMirror ->
                uiEvents.tryEmit(UiEvent.ShowMessage("File shared to some devices. Retry in progress\u2026"))
            is FileShareService.ShareResult.AllConnectorsFailed ->
                uiEvents.tryEmit(UiEvent.ShowMessage("Failed to share file"))
            is FileShareService.ShareResult.NoEligibleConnectors ->
                uiEvents.tryEmit(UiEvent.ShowMessage("No sync service available for file sharing"))
        }
    }

    fun onSaveFile(sharedFile: FileShareInfo.SharedFile, uri: Uri) = launch {
        val ownerDeviceId = deviceIdFlow.value ?: return@launch
        when (val result = fileShareService.saveFile(sharedFile, ownerDeviceId, uri)) {
            is FileShareService.SaveResult.Success ->
                uiEvents.tryEmit(UiEvent.ShowMessage("File saved"))
            is FileShareService.SaveResult.NotAvailable ->
                uiEvents.tryEmit(UiEvent.ShowMessage("File is not available on any of your connected services"))
            is FileShareService.SaveResult.Failed ->
                uiEvents.tryEmit(UiEvent.ShowMessage("Failed to save file: ${result.cause.message}"))
        }
    }

    fun onDeleteFile(sharedFile: FileShareInfo.SharedFile) = launch {
        fileShareService.deleteOwnFile(sharedFile.blobKey)
        uiEvents.tryEmit(UiEvent.ShowMessage("File deleted"))
    }

    companion object {
        private val TAG = logTag("Module", "Files", "List", "VM")
    }
}
