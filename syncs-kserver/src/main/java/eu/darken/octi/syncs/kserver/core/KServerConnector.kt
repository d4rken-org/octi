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
import java.time.Instant


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
        override val activeActions: Int = 0,
        override val lastActionAt: Instant? = null,
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
    private val serverLock = Mutex()

    override val identifier: ConnectorId = ConnectorId(
        type = "kserver",
        subtype = credentials.serverAdress.domain,
        account = credentials.accountId.id,
    )

    init {
        writeQueue
            .onEach { toWrite ->
                runServerAction("write-queue") {
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

                val knownDeviceIds = runServerAction("read-devicelist") {
                    endpoint.listDevices()
                }
                _state.updateBlocking { copy(devices = knownDeviceIds) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to list of known devices: ${e.asLog()}" }
            }
            try {
//                val knownDeviceIds = runServerAction("read-stats") {
//                    endpoint.listDevices()
//                }
//                _state.updateBlocking { copy(devices = knownDeviceIds) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read stats: ${e.asLog()}" }
            }
        }

        if (options.writeData) {
            // TODO
        }

        if (options.readData) {
            log(TAG) { "read()" }
            try {
                runServerAction("read-server") {
                    _data.value = readServer()
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to read: ${e.asLog()}" }
                _state.updateBlocking { copy(lastError = e) }
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

    private suspend fun <R> runServerAction(
        tag: String,
        block: suspend () -> R,
    ): R {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "runServerAction($tag)" }

        return try {
            _state.updateBlocking { copy(activeActions = activeActions + 1) }

            try {
                serverLock.withLock {
                    withContext(NonCancellable) { block() }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "runServerAction($tag) failed: ${e.asLog()}" }
                throw e
            }
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

    @AssistedFactory
    interface Factory {
        fun create(account: KServer.Credentials): KServerConnector
    }

    companion object {
        private val TAG = logTag("Sync", "KServer", "Connector")
    }
}