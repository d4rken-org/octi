package eu.darken.octi.syncs.octiserver.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.collections.fromGzip
import eu.darken.octi.common.collections.toGzip
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncEvent
import eu.darken.octi.sync.core.SyncConnector.EventMode
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.sync.core.SyncWriteContainer
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.ByteString
import java.time.Duration
import java.time.Instant


@Suppress("BlockingMethodInNonBlockingContext")
class OctiServerConnector @AssistedInject constructor(
    @Assisted val credentials: OctiServer.Credentials,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val endpointFactory: OctiServerEndpoint.Factory,
    private val networkStateProvider: NetworkStateProvider,
    private val syncSettings: SyncSettings,
    private val supportedModuleIds: Set<@JvmSuppressWildcards ModuleId>,
    private val baseHttpClient: OkHttpClient,
    private val json: Json,
) : SyncConnector {

    private val endpoint by lazy {
        endpointFactory.create(credentials.serverAdress).also {
            it.setCredentials(credentials)
        }
    }

    private val crypti by lazy { PayloadEncryption(credentials.encryptionKeyset) }

    private fun buildAssociatedData(deviceId: DeviceId, moduleId: ModuleId): ByteArray =
        "${deviceId.id}:${moduleId.id}".toByteArray()

    data class State(
        override val activeActions: Int = 0,
        override val lastActionAt: Instant? = null,
        override val lastError: Exception? = null,
        override val quota: SyncConnectorState.Quota? = null,
        override val devices: Collection<DeviceId>? = null,
        override val isAvailable: Boolean = true,
        override val clockOffsets: List<SyncConnectorState.ClockOffset> = emptyList(),
        val linkedDevices: List<OctiServerEndpoint.LinkedDevice> = emptyList(),
    ) : SyncConnectorState

    private val _state = DynamicStateFlow(
        parentScope = scope + dispatcherProvider.IO,
        loggingTag = TAG,
    ) {
        State()
    }

    override val state: Flow<State> = _state.flow
    private val _data = MutableStateFlow<SyncRead?>(null)
    override val data: Flow<SyncRead?> = _data

    private val writeQueue = MutableSharedFlow<SyncWrite>(extraBufferCapacity = 1)
    private val serverLock = Mutex()

    override val accountLabel: String get() = credentials.serverAdress.domain

    override val identifier: ConnectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = credentials.serverAdress.domain,
        account = credentials.accountId.id,
    )

    private val _syncEventMode = MutableStateFlow(EventMode.NONE)
    override val syncEventMode: StateFlow<EventMode> = _syncEventMode.asStateFlow()

    override val syncEvents: Flow<SyncEvent> = networkStateProvider.networkState
        .map { it.isInternetAvailable }
        .distinctUntilChanged()
        .flatMapLatest { online ->
            if (online) {
                log(TAG, INFO) { "Network available, connecting WebSocket" }
                OctiServerWebSocket(
                    credentials = credentials,
                    connectorId = identifier,
                    syncSettings = syncSettings,
                    baseHttpClient = baseHttpClient,
                    json = json,
                    onConnectionChanged = { connected ->
                        _syncEventMode.value = if (connected) EventMode.LIVE else EventMode.NONE
                    },
                ).connect()
            } else {
                log(TAG, INFO) { "Network lost, WebSocket inactive" }
                _syncEventMode.value = EventMode.NONE
                emptyFlow()
            }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    init {
        writeQueue
            .onEach { toWrite ->
                try {
                    runServerAction("write-queue") {
                        writeServer(toWrite)
                    }
                } catch (e: OctiServerHttpException) {
                    if (e.isDeviceUnknown) {
                        log(TAG, WARN) { "Write failed: device no longer registered, pausing" }
                        pauseConnector()
                    } else {
                        throw e
                    }
                }
            }
            .retry {
                delay(5000)
                true
            }
            .setupCommonEventHandlers(TAG) { "writeQueue" }
            .launchIn(scope)
    }

    private suspend fun isInternetAvailable() = networkStateProvider.networkState.first().isInternetAvailable

    override suspend fun write(toWrite: SyncWrite) {
        log(TAG) { "write(toWrite=$toWrite)" }
        writeQueue.emit(toWrite)
    }

    override suspend fun resetData() {
        log(TAG, INFO) { "resetData()" }
        runServerAction("reset-devices") {
            endpoint.resetDevices()
        }
    }

    override suspend fun deleteDevice(deviceId: DeviceId) {
        log(TAG, INFO) { "deleteDevice(deviceId=$deviceId)" }
        runServerAction("delete-device-$deviceId") {
            endpoint.deleteDevice(deviceId)
        }
    }

    suspend fun createLinkCode(): LinkingData {
        log(TAG) { "createLinkCode()" }
        val linkCode = endpoint.createLinkCode()

        return LinkingData(
            serverAdress = credentials.serverAdress,
            linkCode = linkCode,
            encryptionKeyset = credentials.encryptionKeyset,
        )
    }

    override suspend fun sync(options: SyncOptions) {
        log(TAG) { "sync(options=$options)" }

        if (!isInternetAvailable()) {
            log(TAG, WARN) { "sync(): Skipping, we are offline." }
            return
        }

        if (options.stats) {
            try {
                val linked = runServerAction("read-devicelist") {
                    endpoint.listDevices()
                }
                _state.updateBlocking {
                    copy(
                        devices = linked.map { it.deviceId },
                        linkedDevices = linked.toList(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (handleDeviceUnknown(e, "list known devices")) return
                log(TAG, ERROR) { "Failed to list known devices: ${e.asLog()}" }
            }
        }

        if (options.writeData && options.writePayload.isNotEmpty()) {
            log(TAG) { "sync(): Writing ${options.writePayload.size} cached modules" }
            options.writePayload.forEach { module ->
                try {
                    runServerAction("write-cached-${module.moduleId}") {
                        writeServer(SyncWriteContainer(deviceId = syncSettings.deviceId, modules = listOf(module)))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (handleDeviceUnknown(e, "write cached ${module.moduleId}")) return
                    log(TAG, ERROR) { "Failed to write cached ${module.moduleId}: ${e.asLog()}" }
                }
            }
        }

        if (options.readData) {
            val moduleFilter = options.moduleFilter
            val deviceFilter = options.deviceFilter
            val isTargeted = moduleFilter != null || deviceFilter != null
            log(TAG) { "read(moduleFilter=$moduleFilter, deviceFilter=$deviceFilter)" }
            try {
                runServerAction("read-server") {
                    val newData = readServer(moduleFilter, deviceFilter)
                    val existing = _data.value
                    _data.value = if (isTargeted && existing != null) {
                        mergeData(existing, newData, moduleFilter)
                    } else {
                        newData
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (handleDeviceUnknown(e, "read")) return
                log(TAG, ERROR) { "Failed to read: ${e.asLog()}" }
            }
        }
    }

    private suspend fun fetchModule(deviceId: DeviceId, moduleId: ModuleId): OctiServerModuleData? {
        val readData = endpoint.readModule(deviceId = deviceId, moduleId = moduleId) ?: return null

        if (readData.serverTime != null) {
            val serverTime = readData.serverTime
            val offset = Duration.between(serverTime, readData.localTime)
            log(TAG, VERBOSE) { "fetchModule($deviceId:$moduleId): serverTime=$serverTime, offset=${offset.seconds}s" }
            val clockOffset = SyncConnectorState.ClockOffset(offset = offset, measuredAt = readData.localTime)
            _state.updateBlocking { copy(clockOffsets = clockOffsets + clockOffset) }
        } else {
            log(TAG, VERBOSE) { "fetchModule($deviceId:$moduleId): no serverTime in response" }
        }

        val payload = if (readData.payload != ByteString.EMPTY) {
            crypti.decrypt(readData.payload, buildAssociatedData(deviceId, moduleId)).fromGzip()
        } else {
            ByteString.EMPTY
        }

        return OctiServerModuleData(
            connectorId = identifier,
            deviceId = deviceId,
            moduleId = moduleId,
            modifiedAt = readData.modifiedAt,
            payload = payload,
        ).also { log(TAG, VERBOSE) { "readServer(): Module data: $it" } }
    }

    private suspend fun readServer(
        moduleFilter: Set<ModuleId>? = null,
        deviceFilter: Set<DeviceId>? = null,
    ): OctiServerData {
        log(TAG, DEBUG) { "readServer(moduleFilter=$moduleFilter, deviceFilter=$deviceFilter): Starting..." }
        _state.updateBlocking { copy(clockOffsets = emptyList()) }
        val allLinkedDevices = endpoint.listDevices()
        val allDeviceIds = allLinkedDevices.map { it.deviceId }
        val deviceIds = if (deviceFilter != null) {
            allDeviceIds.filter { it in deviceFilter }
        } else {
            allDeviceIds
        }
        log(TAG, VERBOSE) { "readServer(): Found devices: $deviceIds (${allDeviceIds.size} total)" }

        val targetModuleIds = moduleFilter ?: supportedModuleIds
        val isTargeted = moduleFilter != null

        val devices = deviceIds.map { deviceId ->
            scope.async moduleFetch@{
                val moduleFetchJobs = targetModuleIds.map { moduleId ->
                    val fetchResult = try {
                        fetchModule(deviceId, moduleId)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Failed to fetch: $deviceId:$moduleId:\n${e.asLog()}" }
                        null
                    }
                    log(TAG, VERBOSE) { "Module fetched: $fetchResult" }
                    if (!isTargeted) delay(1000)
                    fetchResult
                }

                val modules = moduleFetchJobs.filterNotNull()

                OctiServerDeviceData(
                    deviceId = deviceId,
                    modules = modules,
                )
            }
        }.awaitAll()

        return OctiServerData(
            connectorId = identifier,
            devices = devices
        )
    }

    private suspend fun writeServer(data: SyncWrite) {
        log(TAG, DEBUG) { "writeServer(): $data)" }

        // TODO cache write data for when we are online again?
        if (!isInternetAvailable()) {
            log(TAG, WARN) { "writeServer(): Skipping, we are offline." }
            return
        }

        data.modules.forEach { module ->
            endpoint.writeModule(
                moduleId = module.moduleId,
                payload = crypti.encrypt(module.payload.toGzip(), buildAssociatedData(data.deviceId, module.moduleId)),
            )
        }
        log(TAG, VERBOSE) { "writeServer(): Done" }
    }

    private suspend fun handleDeviceUnknown(e: Exception, operation: String): Boolean {
        if ((e as? OctiServerHttpException)?.isDeviceUnknown != true) return false
        log(TAG, WARN) { "$operation: device no longer registered, pausing connector" }
        pauseConnector()
        return true
    }

    private suspend fun pauseConnector() {
        runCatching {
            syncSettings.pausedConnectors.update { it + identifier }
        }.onFailure {
            log(TAG, ERROR) { "Failed to pause connector: ${it.asLog()}" }
        }
    }

    private suspend fun <R> runServerAction(
        tag: String,
        block: suspend () -> R,
    ): R {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "runServerAction($tag)" }

        return try {
            _state.updateBlocking { copy(activeActions = activeActions + 1) }

            serverLock.withLock {
                withContext(NonCancellable) { block() }
            }.also {
                _state.updateBlocking { copy(lastError = null) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "runServerAction($tag) failed: ${e.asLog()}" }
            _state.updateBlocking { copy(lastError = e) }
            throw e
        } finally {
            _state.updateBlocking {
                log(TAG, VERBOSE) { "runServerAction($tag) finished" }
                copy(
                    activeActions = activeActions - 1,
                    lastActionAt = Instant.now(),
                )
            }
            log(TAG, VERBOSE) { "runServerAction($tag) finished after ${System.currentTimeMillis() - start}ms" }
        }
    }

    internal fun mergeData(
        existing: SyncRead,
        update: SyncRead,
        moduleFilter: Set<ModuleId>?,
    ): OctiServerData {
        val updatedDeviceMap = update.devices.associateBy { it.deviceId }
        val existingDeviceIds = existing.devices.map { it.deviceId }.toSet()

        val mergedDevices = existing.devices.map { existingDevice ->
            val updatedDevice = updatedDeviceMap[existingDevice.deviceId]
            if (updatedDevice == null) {
                existingDevice
            } else {
                val keptModules = if (moduleFilter != null) {
                    existingDevice.modules.filter { it.moduleId !in moduleFilter }
                } else {
                    emptyList()
                }
                OctiServerDeviceData(
                    deviceId = existingDevice.deviceId,
                    modules = keptModules + updatedDevice.modules,
                )
            }
        }

        val newDevices = update.devices
            .filter { it.deviceId !in existingDeviceIds }
            .map { OctiServerDeviceData(deviceId = it.deviceId, modules = it.modules.toList()) }

        return OctiServerData(connectorId = existing.connectorId, devices = mergedDevices + newDevices)
    }

    @AssistedFactory
    interface Factory {
        fun create(account: OctiServer.Credentials): OctiServerConnector
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "Connector")
    }
}