package eu.darken.octi.sync.ui.devices

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaSerializer
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.getConnectorById
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SyncDevicesVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    syncManager: SyncManager,
    private val syncSettings: SyncSettings,
    private val manager: SyncManager,
    private val metaSerializer: MetaSerializer,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<SyncDevicesFragmentArgs>()

    private val connectorId: ConnectorId
        get() = navArgs.connectorId

    data class State(
        val items: List<SyncDevicesAdapter.Item> = emptyList()
    )

    val state = manager.getConnectorById<SyncConnector>(connectorId)
        .catch { if (it is NoSuchElementException) popNavStack() else throw it }
        .flatMapLatest { connector ->
            connector.state
                .distinctUntilChangedBy { it.devices }
                .flatMapLatest { state ->
                    connector.data
                        .map { data ->
                            data?.devices
                                ?.flatMap { it.modules }
                                ?.filter { it.moduleId == MetaModule.MODULE_ID }
                        }
                        .map { metaDatas ->
                            val items = mutableListOf<SyncDevicesAdapter.Item>()
                            log(TAG) { "Loading devices for $state" }

                            var error: Exception? = null
                            state.devices?.map { deviceId ->
                                var lastSeen: Instant? = null
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
                                DefaultSyncDeviceVH.Item(
                                    deviceId = deviceId,
                                    metaInfo = metaInfo,
                                    lastSeen = lastSeen,
                                    error = error,
                                    onClick = {
                                        SyncDevicesFragmentDirections.actionSyncDevicesFragmentToDeviceActionsFragment(
                                            connectorId, deviceId
                                        ).navigate()
                                    },
                                )
                            }
                                ?.sortedBy { it.metaInfo?.labelOrFallback?.lowercase() }
                                ?.run { items.addAll(this) }
                            State(items)
                        }
                }
        }.asLiveData2()

    companion object {
        private val TAG = logTag("Sync", "Devices", "Fragment", "VM")
    }
}