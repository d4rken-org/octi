package eu.darken.octi.sync.ui.devices

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaSerializer
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SyncDevicesVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @AppScope private val appScope: CoroutineScope,
    private val syncSettings: SyncSettings,
    private val manager: SyncManager,
    private val metaSerializer: MetaSerializer,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val connectorIdFlow = MutableStateFlow<String?>(null)

    private val connectorFlow = connectorIdFlow
        .filterNotNull()
        .flatMapLatest { idStr ->
            manager.connectors.map { connectors ->
                connectors.single { it.identifier.idString == idStr }
            }
        }
        .catch { if (it is NoSuchElementException) navUp() else throw it }
        .replayingShare(vmScope)

    data class DeviceItem(
        val deviceId: DeviceId,
        val metaInfo: MetaInfo?,
        val lastSeen: Instant?,
        val error: Exception?,
    )

    data class State(
        val items: List<DeviceItem> = emptyList(),
        val connectorType: String? = null,
    )

    val state = connectorFlow
        .flatMapLatest { connector ->
            connector.state
                .distinctUntilChangedBy { it.devices }
                .flatMapLatest { connState ->
                    connector.data
                        .map { data ->
                            data?.devices
                                ?.flatMap { it.modules }
                                ?.filter { it.moduleId == MetaModule.MODULE_ID }
                        }
                        .map { metaDatas ->
                            log(TAG) { "Loading devices for $connState" }

                            val items = connState.devices?.map { deviceId ->
                                var lastSeen: Instant? = null
                                var error: Exception? = null
                                val metaInfo = metaDatas?.find { it.deviceId == deviceId }?.let {
                                    lastSeen = it.modifiedAt
                                    try {
                                        metaSerializer.deserialize(it.payload)
                                    } catch (e: Exception) {
                                        log(TAG, ERROR) { "Failed to deserialize MetaInfo:\n${e.asLog()}" }
                                        error = e
                                        null
                                    }
                                }
                                DeviceItem(
                                    deviceId = deviceId,
                                    metaInfo = metaInfo,
                                    lastSeen = lastSeen,
                                    error = error,
                                )
                            }
                                ?.sortedBy { it.metaInfo?.labelOrFallback?.lowercase() }
                                ?: emptyList()

                            State(
                                items = items,
                                connectorType = connector.identifier.type,
                            )
                        }
                }
        }
        .shareLatest(scope = vmScope)

    fun initialize(connectorId: String) {
        if (connectorIdFlow.value != null) return
        connectorIdFlow.value = connectorId
    }

    fun deleteDevice(deviceId: DeviceId) = launch {
        log(TAG, INFO) { "Deleting device $deviceId" }
        appScope.launch {
            try {
                if (syncSettings.deviceId == deviceId) {
                    log(TAG, WARN) { "We are deleting US, doing disconnect instead of delete" }
                    val connector = connectorFlow.first()
                    manager.disconnect(connector.identifier)
                } else {
                    connectorFlow.first().apply {
                        deleteDevice(deviceId)
                        sync(SyncOptions(writeData = false))
                    }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to delete device $deviceId: ${e.asLog()}" }
                errorEvents.tryEmit(e)
            }
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Devices", "VM")
    }
}