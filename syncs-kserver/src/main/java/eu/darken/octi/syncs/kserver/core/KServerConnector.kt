package eu.darken.octi.syncs.kserver.core

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
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncConnectorState
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncRead
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import eu.darken.octi.sync.core.encryption.PayloadEncryption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString
import java.time.Duration
import java.time.Instant
import kotlin.math.max


@Suppress("BlockingMethodInNonBlockingContext")
class KServerConnector @AssistedInject constructor(
    @Assisted val credentials: KServer.Credentials,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val endpointFactory: KServerEndpoint.Factory,
    private val networkStateProvider: NetworkStateProvider,
    private val syncSettings: SyncSettings,
    private val supportedModuleIds: Set<@JvmSuppressWildcards ModuleId>,
) : SyncConnector {

    private val endpoint by lazy {
        endpointFactory.create(credentials.serverAdress).also {
            it.setCredentials(credentials)
        }
    }

    private val crypti by lazy { PayloadEncryption(credentials.encryptionKeyset) }

    data class State(
        override val readActions: Int = 0,
        override val writeActions: Int = 0,
        override val lastReadAt: Instant? = null,
        override val lastWriteAt: Instant? = null,
        override val lastError: Exception? = null,
        override val quota: SyncConnectorState.Quota? = null,
        override val devices: Collection<DeviceId>? = null,
        override val isAvailable: Boolean = true,
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

    private val writeQueue = MutableSharedFlow<SyncWrite>()
    private val writeLock = Mutex()
    private val readLock = Mutex()

    override val identifier: ConnectorId = ConnectorId(
        type = "kserver",
        subtype = credentials.serverAdress.domain,
        account = credentials.accountId.id,
    )

    init {
        writeQueue
            .onEach { toWrite ->
                writeServerWrapper {
                    writeServer(toWrite)
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

    override suspend fun resetData() = writeServerWrapper {
        log(TAG, INFO) { "resetData()" }
        endpoint.resetDevices()
    }

    override suspend fun deleteDevice(deviceId: DeviceId) = writeServerWrapper {
        log(TAG, INFO) { "deleteDevice(deviceId=$deviceId)" }
        endpoint.deleteDevice(deviceId)
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

        if (options.writeData) {
            // TODO
        }

        if (options.readData) {
            log(TAG) { "read()" }
            try {
                readServerWrapper {
                    _data.value = readServer()
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read: ${e.asLog()}" }
                _state.updateBlocking { copy(lastError = e) }
            }
        }

        if (options.stats) {
            try {
                val knownDeviceIds = endpoint.listDevices()
                _state.updateBlocking { copy(devices = knownDeviceIds) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to list of known devices: ${e.asLog()}" }
            }
        }
    }

    private suspend fun fetchModule(deviceId: DeviceId, moduleId: ModuleId): KServerModuleData? {
        val readData = endpoint.readModule(deviceId = deviceId, moduleId = moduleId) ?: return null

        val payload = if (readData.payload != ByteString.EMPTY) {
            crypti.decrypt(readData.payload).fromGzip()
        } else {
            ByteString.EMPTY
        }

        return KServerModuleData(
            connectorId = identifier,
            deviceId = deviceId,
            moduleId = moduleId,
            modifiedAt = readData.modifiedAt,
            payload = payload,
        ).also { log(TAG, VERBOSE) { "readServer(): Module data: $it" } }
    }

    private suspend fun readServer(): KServerData {
        log(TAG, DEBUG) { "readServer(): Starting..." }
        val deviceIds = endpoint.listDevices()
        log(TAG, VERBOSE) { "readServer(): Found devices: $deviceIds" }

        val devices = deviceIds.map { deviceId ->
            scope.async moduleFetch@{
                val moduleFetchJobs = supportedModuleIds.map { moduleId ->
                    val fetchResult = try {
                        fetchModule(deviceId, moduleId)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Failed to fetch: $deviceId:$moduleId:\n${e.asLog()}" }
                        null
                    }
                    log(TAG, VERBOSE) { "Module fetched: $fetchResult" }
                    delay(1000)
                    fetchResult
                }

                val modules = moduleFetchJobs.filterNotNull()

                KServerDeviceData(
                    deviceId = deviceId,
                    modules = modules,
                )
            }
        }.awaitAll()

        return KServerData(
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
                payload = crypti.encrypt(module.payload.toGzip()),
            )
        }
        log(TAG, VERBOSE) { "writeServer(): Done" }
    }

    private fun getStorageStats(): SyncConnectorState.Quota {
        log(TAG, VERBOSE) { "getStorageStats()" }

        return SyncConnectorState.Quota()
    }

    private suspend fun readServerWrapper(block: suspend () -> Unit) {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "readAction(block=$block)" }

        var newStorageQuota: SyncConnectorState.Quota? = null

        if (_state.value().readActions > 0) {
            log(TAG, WARN) { "Already executing read skipping." }
            return
        }
        try {
            _state.updateBlocking {
                copy(readActions = readActions + 1)
            }

            block()

            val lastStats = _state.value().quota?.updatedAt
            if (lastStats == null || Duration.between(lastStats, Instant.now()) > Duration.ofSeconds(60)) {
                log(TAG) { "readAction(block=$block): Updating storage stats" }
                newStorageQuota = getStorageStats()
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "readAction(block=$block) failed: ${e.asLog()}" }
            throw e
        } finally {
            _state.updateBlocking {
                copy(
                    readActions = max(readActions - 1, 0),
                    quota = newStorageQuota ?: quota,
                    lastReadAt = Instant.now(),
                )
            }
        }

        log(TAG, VERBOSE) { "readAction(block=$block) finished after ${System.currentTimeMillis() - start}ms" }
    }

    private suspend fun writeServerWrapper(block: suspend () -> Unit) = withContext(NonCancellable) {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "writeAction(block=$block)" }

        _state.updateBlocking { copy(writeActions = writeActions + 1) }

        try {
            writeLock.withLock {
                try {
                    block()
                } catch (e: Exception) {
                    log(TAG, ERROR) { "writeAction(block=$block) failed: ${e.asLog()}" }
                    throw e
                }
            }
        } finally {
            _state.updateBlocking {
                log(TAG, VERBOSE) { "writeAction(block=$block) finished" }
                copy(
                    writeActions = writeActions - 1,
                    lastWriteAt = Instant.now(),
                )
            }
            log(TAG, VERBOSE) { "writeAction(block=$block) finished after ${System.currentTimeMillis() - start}ms" }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(account: KServer.Credentials): KServerConnector
    }

    companion object {
        private val TAG = logTag("Sync", "KServer", "Connector")
    }
}