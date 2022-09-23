package eu.darken.octi.syncs.jserver.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.*
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.sync.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant


@Suppress("BlockingMethodInNonBlockingContext")
class JServerConnector @AssistedInject constructor(
    @Assisted val credentials: JServer.Credentials,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val endpointFactory: JServerEndpoint.Factory,
    private val syncSettings: SyncSettings,
) : SyncConnector {

    private val endpoint by lazy {
        endpointFactory.create(credentials.serverAdress).also {
            it.setCredentials(credentials)
        }
    }

    data class State(
        override val readActions: Int = 0,
        override val writeActions: Int = 0,
        override val lastReadAt: Instant? = null,
        override val lastWriteAt: Instant? = null,
        override val lastError: Exception? = null,
        override val stats: SyncConnectorState.Stats? = null,
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
        "${credentials.serverAdress.domain}(${credentials.accountId.id})"
    )

    init {
        writeQueue
            .onEach { toWrite ->
                writeAction {
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

    override suspend fun read() {
        log(TAG) { "read()" }
        try {
            readAction {
                _data.value = readServer()
            }
        } catch (e: Exception) {
            _state.updateBlocking { copy(lastError = e) }
        }
    }

    override suspend fun write(toWrite: SyncWrite) {
        log(TAG) { "write(toWrite=$toWrite)" }
        writeQueue.emit(toWrite)
    }

    override suspend fun deleteAll() {
        log(TAG, INFO) { "wipe()" }
        writeAction {
            // TODO
        }
    }

    override suspend fun deleteDevice(deviceId: DeviceId) {
        log(TAG, INFO) { "deleteDevice(deviceId=$deviceId)" }
        writeAction {
            // TODO
        }
    }

    suspend fun createLinkCode(): LinkCodeContainer {
        log(TAG) { "createLinkCode()" }
        val linkCode = endpoint.createLinkCode()

        return LinkCodeContainer(
            serverAdress = credentials.serverAdress,
            accountId = credentials.accountId,
            fromDeviceId = syncSettings.deviceId,
            linkCode = linkCode
        )
    }

    private suspend fun readServer(): JServerData {
        log(TAG, DEBUG) { "readServer(): Starting..." }
        throw Exception("TODO")
    }

    private suspend fun writeServer(data: SyncWrite) {
        log(TAG, DEBUG) { "writeServer(): $data)" }
        throw Exception("TODO")
        log(TAG, VERBOSE) { "writeServer(): Done" }
    }

    private fun getStorageStats(): SyncConnectorState.Stats {
        log(TAG, VERBOSE) { "getStorageStats()" }

        return SyncConnectorState.Stats()
    }

    private suspend fun readAction(block: suspend () -> Unit) {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "readAction(block=$block)" }

        _state.updateBlocking { copy(readActions = readActions + 1) }

        var newStorageStats: SyncConnectorState.Stats? = null

        try {
            block()

            val lastStats = _state.value().stats?.timestamp
            if (lastStats == null || Duration.between(lastStats, Instant.now()) > Duration.ofSeconds(60)) {
                log(TAG) { "readAction(block=$block): Updating storage stats" }
                newStorageStats = getStorageStats()
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "readAction(block=$block) failed: ${e.asLog()}" }
            throw e
        } finally {
            _state.updateBlocking {
                copy(
                    readActions = readActions - 1,
                    stats = newStorageStats ?: stats,
                    lastReadAt = Instant.now(),
                )
            }
        }

        log(TAG, VERBOSE) { "readAction(block=$block) finished after ${System.currentTimeMillis() - start}ms" }
    }

    private suspend fun writeAction(block: suspend () -> Unit) = withContext(NonCancellable) {
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
        fun create(account: JServer.Credentials): JServerConnector
    }

    companion object {
        private val TAG = logTag("Sync", "JServer", "Connector")
    }
}