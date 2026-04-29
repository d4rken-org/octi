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
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.modules.meta.MetaModule
import eu.darken.octi.modules.meta.core.MetaInfo
import eu.darken.octi.modules.meta.core.MetaSerializer
import eu.darken.octi.sync.core.ConnectorCommand
import eu.darken.octi.sync.core.ConnectorOperation
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.DeviceRemovalPolicy
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.ConnectorIssue
import eu.darken.octi.sync.core.ConnectorIssueAggregator
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Instant

@HiltViewModel
class SyncDevicesVM @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    @AppScope private val appScope: CoroutineScope,
    private val syncSettings: SyncSettings,
    private val manager: SyncManager,
    private val metaSerializer: MetaSerializer,
    private val issueAggregator: ConnectorIssueAggregator,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val connectorIdFlow = MutableStateFlow<String?>(null)

    private val connectorFlow = connectorIdFlow
        .filterNotNull()
        .flatMapLatest { idStr ->
            manager.allConnectors.map { connectors ->
                connectors.singleOrNull { it.identifier.idString == idStr }
                    ?: throw NoSuchElementException("No connector for $idStr")
            }
        }
        .catch { if (it is NoSuchElementException) navUp() else throw it }
        .replayingShare(vmScope)

    init {
        // Bridge Failed DeleteDevice completions into the standard error dialog pipeline,
        // then dismiss so the operation list stays tidy. Uses the completions flow (every
        // terminal emitted exactly once) rather than scanning the bounded operations list.
        connectorFlow
            .flatMapLatest { it.completions }
            .filterIsInstance<ConnectorOperation.Failed>()
            .filter { it.command is ConnectorCommand.DeleteDevice }
            .onEach { failed ->
                log(TAG, WARN) { "DeleteDevice failed: ${failed.error.asLog()}" }
                errorEvents.tryEmit(failed.error)
                connectorFlow.first().dismiss(failed.id)
            }
            .launchIn(vmScope)
    }

    data class DeviceItem(
        val deviceId: DeviceId,
        val metaInfo: MetaInfo?,
        val lastSeen: Instant?,
        val error: Exception?,
        val serverVersion: String?,
        val serverAddedAt: Instant?,
        val serverPlatform: String?,
        val issues: List<ConnectorIssue> = emptyList(),
    )

    data class State(
        val items: List<DeviceItem> = emptyList(),
        val deviceRemovalPolicy: DeviceRemovalPolicy? = null,
        val isPaused: Boolean = false,
        val deletingDeviceIds: Set<DeviceId> = emptySet(),
    )

    val state = connectorFlow
        .flatMapLatest { connector ->
            combine(
                connector.state.map { it.deviceMetadata },
                connector.data.map { data ->
                    data?.devices
                        ?.flatMap { it.modules }
                        ?.filter { it.moduleId == MetaModule.MODULE_ID }
                },
                connector.operations,
                issueAggregator.issues,
                syncSettings.pausedConnectorIds,
            ) { deviceMetadata, metaDatas, operations, allIssues, pausedIds ->
                log(TAG) { "Loading devices, ${deviceMetadata.size} metadata, ${allIssues.size} issues" }

                val connectorId = connector.identifier
                val items = deviceMetadata.map { deviceMeta ->
                    val deviceId = deviceMeta.deviceId
                    var error: Exception? = null
                    val metaInfo = metaDatas?.find { it.deviceId == deviceId }?.let {
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
                        lastSeen = deviceMeta.lastSeen,
                        error = error,
                        serverVersion = deviceMeta.version,
                        serverAddedAt = deviceMeta.addedAt,
                        serverPlatform = deviceMeta.platform,
                        issues = allIssues.filter { it.connectorId == connectorId && it.deviceId == deviceId },
                    )
                }.sortedBy { it.metaInfo?.labelOrFallback?.lowercase() }

                val deletingIds = operations
                    .filter { it is ConnectorOperation.Queued || it is ConnectorOperation.Processing }
                    .mapNotNullTo(mutableSetOf()) { (it.command as? ConnectorCommand.DeleteDevice)?.deviceId }

                State(
                    items = items,
                    deviceRemovalPolicy = connector.capabilities.deviceRemovalPolicy,
                    isPaused = pausedIds.contains(connectorId),
                    deletingDeviceIds = deletingIds,
                )
            }
        }
        .asStateFlow()

    fun initialize(connectorId: String) {
        if (connectorIdFlow.value != null) return
        connectorIdFlow.value = connectorId
    }

    fun deleteDevice(deviceId: DeviceId) = launch {
        log(TAG, INFO) { "Deleting device $deviceId" }
        val connector = connectorFlow.first()
        if (syncSettings.deviceId == deviceId) {
            log(TAG, WARN) { "Self-delete — routing through manager.disconnect()" }
            appScope.launch {
                runCatching { manager.disconnect(connector.identifier) }
                    .onFailure { errorEvents.tryEmit(it) }
            }
            return@launch
        }
        // Non-suspending submit. The processor serializes against Pause/etc. and prunes the
        // device from connector state + data, which drops the row from state.items — the UI
        // auto-dismisses the sheet. Failure surfaces via the completions bridge in init.
        connector.submit(ConnectorCommand.DeleteDevice(deviceId))
    }

    companion object {
        private val TAG = logTag("Sync", "Devices", "VM")
    }
}
