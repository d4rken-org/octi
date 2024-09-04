package eu.darken.octi.sync.ui.devices.actions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaSerializer
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.getConnectorById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceActionsVM @Inject constructor(
    handle: SavedStateHandle,
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val syncSettings: SyncSettings,
    private val metaSerializer: MetaSerializer,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs: DeviceActionsFragmentArgs by handle.navArgs()

    data class State(
        val deviceId: DeviceId,
        val metaInfo: MetaInfo?,
        val removeIsRevoke: Boolean?,
    )

    init {
        log(TAG) { "Loading for ${navArgs.deviceId} on ${navArgs.connectorId}" }
    }

    private val connectorFlow: Flow<SyncConnector> = syncManager
        .getConnectorById<SyncConnector>(navArgs.connectorId)
        .catch {
            if (it is NoSuchElementException) popNavStack() else errorEvents.postValue(it)
        }
        .replayingShare(viewModelScope)

    val state = connectorFlow.map { connector ->
        val metaInfo = connector.data.firstOrNull()
            ?.devices
            ?.firstOrNull { it.deviceId == navArgs.deviceId }
            ?.modules
            ?.firstOrNull { it.moduleId == MetaModule.MODULE_ID }
            ?.let {
                try {
                    metaSerializer.deserialize(it.payload)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Failed to deserialize MetaInfo for ${navArgs.deviceId}" }
                    null
                }
            }
        State(
            deviceId = navArgs.deviceId,
            metaInfo = metaInfo,
            removeIsRevoke = when (connector.identifier.type) {
                "gdrive" -> false
                "kserver" -> true
                else -> null
            }
        )
    }.asLiveData2()

    fun deleteDevice() = launch {
        log(TAG, INFO) { "Deleting device ${navArgs.deviceId}" }
        appScope.launch {
            if (syncSettings.deviceId == navArgs.deviceId) {
                log(TAG, WARN) { "We are deleting US, doing disconnect instead of delete" }
                syncManager.disconnect(navArgs.connectorId)
            } else {
                connectorFlow.first().apply {
                    deleteDevice(navArgs.deviceId)
                    sync(SyncOptions(writeData = false))
                }
            }
        }
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Sync", "Devices", "Device", "Actions", "Fragment", "VM")
    }
}