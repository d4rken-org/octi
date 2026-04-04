package eu.darken.octi.sync.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.shareIn
import eu.darken.octi.sync.core.cache.SyncCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val syncCache: SyncCache,
    private val connectorHubs: Set<@JvmSuppressWildcards ConnectorHub>,
    private val connectorSyncState: ConnectorSyncState,
) {

    private val syncLock = Mutex()
    private val pendingSync = AtomicBoolean(false)
    private val modulePayloads = ConcurrentHashMap<ModuleId, SyncWrite.Device.Module>()

    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Suppress("MagicNumber")
    val pendingSyncTrigger: Flow<Unit> = syncRequests.debounce(2_000L)

    private val disabledConnectors = MutableStateFlow(emptySet<SyncConnector>())

    private val hubs: Flow<Collection<ConnectorHub>> = flow {
        emit(connectorHubs)
        awaitCancellation()
    }
        .setupCommonEventHandlers(TAG) { "syncHubs" }
        .shareLatest(scope + dispatcherProvider.Default)

    val connectors: Flow<List<SyncConnector>> = combine(
        hubs.flatMapLatest { hs -> combine(hs.map { it.connectors }) { it.toList().flatten() } },
        disabledConnectors
    ) { connectors, disabledConnectors ->
        connectors.filter { !disabledConnectors.contains(it) }
    }
        .setupCommonEventHandlers(TAG) { "syncConnectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    val states: Flow<Collection<SyncConnectorState>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }
        .shareLatest(scope + dispatcherProvider.Default)

    val syncEvents: Flow<SyncEvent> = syncSettings.pausedConnectors.flow
        .combine(connectors) { paused, connectorList ->
            connectorList.filter { !paused.contains(it.identifier) }
        }
        .flatMapLatest { cons ->
            if (cons.isEmpty()) emptyFlow()
            else cons.map { it.syncEvents }.merge()
        }
        .setupCommonEventHandlers(TAG) { "syncEvents" }
        .shareIn(scope + dispatcherProvider.Default, SharingStarted.WhileSubscribed(), replay = 0)

    val data: Flow<Collection<SyncRead.Device>> = syncSettings.pausedConnectors.flow
        .combine(connectors) { paused, connectorList -> connectorList.filter { !paused.contains(it.identifier) } }
        .flatMapLatest { connectorList ->
            if (connectorList.isEmpty()) {
                flowOf(emptyList())
            } else {
                val connectorDataFlows: List<Flow<Pair<ConnectorId, SyncRead?>>> = connectorList.map { con ->
                    con.data.map { syncRead -> con.identifier to syncRead }
                }
                // Combine all new emissions
                combine(connectorDataFlows) { it.toSet() }
            }
        }
        .map { pairs ->
            pairs.mapNotNull { (id, read) ->
                if (read != null) syncCache.save(id, read)
                read ?: syncCache.load(id)
            }
        }
        .map { it.latestData() }
        .setupCommonEventHandlers(TAG) { "syncData" }
        .shareLatest(scope + dispatcherProvider.Default)

    suspend fun sync(options: SyncOptions = SyncOptions()) {
        log(TAG) { "sync(${options.logLabel})" }
        if (!syncLock.tryLock()) {
            pendingSync.set(true)
            log(TAG) { "Sync already in progress, flagged for re-run" }
            return
        }
        try {
            do {
                pendingSync.set(false)
                val syncJobs = connectors.first()
                    .filter {
                        val paused = syncSettings.pausedConnectors.value().contains(it.identifier)
                        if (paused) log(TAG, INFO) { "Connector is paused: $it" }
                        !paused
                    }
                    .map {
                        scope.launch {
                            try {
                                sync(it.identifier, options = options)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                log(TAG, ERROR) { "sync(): ${it.identifier} failed: ${e.asLog()}" }
                            }
                        }
                    }
                syncJobs.joinAll()
            } while (pendingSync.getAndSet(false))
        } finally {
            syncLock.unlock()
        }
    }

    suspend fun sync(connectorId: ConnectorId, options: SyncOptions = SyncOptions()) {
        log(TAG) { "sync(${connectorId.logLabel}, ${options.logLabel})" }
        val connector = connectors.first().singleOrNull { it.identifier == connectorId }
        if (connector == null) {
            log(TAG, WARN) { "sync(): Connector $connectorId not found, skipping" }
            return
        }

        val changedModules = if (options.writeData) {
            modulePayloads.values.mapNotNull { module ->
                val currentHash = module.payload.sha256().hex()
                val lastSent = connectorSyncState.getHash(connectorId, module.moduleId)
                if (currentHash != lastSent) module to currentHash else null
            }
        } else {
            emptyList()
        }

        val effectiveOptions = if (changedModules.isNotEmpty()) {
            log(TAG) { "sync(): Passing ${changedModules.size} changed modules to $connectorId" }
            options.copy(writePayload = changedModules.map { it.first })
        } else {
            options
        }

        connector.sync(effectiveOptions)

        changedModules.forEach { (module, hash) ->
            connectorSyncState.setHash(connectorId, module.moduleId, hash)
        }
    }

    fun updatePayload(payload: SyncWrite.Device.Module) {
        log(TAG) { "updatePayload(moduleId=${payload.moduleId})" }
        modulePayloads[payload.moduleId] = payload
    }

    fun requestSync() {
        syncRequests.tryEmit(Unit)
    }

    suspend fun resetData(identifier: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "resetData(identifier=$identifier)" }
        getConnectorById<SyncConnector>(identifier).first().resetData()
        log(TAG) { "resetData(identifier=$identifier) done" }
    }

    suspend fun disconnect(identifier: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "disconnect(identifier=$identifier)" }

        connectorSyncState.clearConnector(identifier)

        val connector = getConnectorById<SyncConnector>(identifier).first()

        disabledConnectors.value += connector

        if (syncSettings.pausedConnectors.value().contains(identifier)) {
            log(TAG) { "disconnect(...) was paused, clearing it" }
            syncSettings.pausedConnectors.update { it - identifier }
        }

        try {
            hubs.first().filter { it.owns(identifier) }.forEach {
                it.remove(identifier)
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "disconnect(...) failed: ${e.asLog()}" }
            throw e
        } finally {
            disabledConnectors.value -= connector
        }

        log(TAG) { "disconnect(connector=$connector) done" }
    }

    suspend fun togglePause(identifier: ConnectorId, paused: Boolean? = null) {
        log(TAG, INFO) { "togglePause($identifier, enabled=$paused)" }
        val pause = paused ?: !syncSettings.pausedConnectors.value().contains(identifier)
        when (pause) {
            true -> syncSettings.pausedConnectors.update {
                it + identifier
            }

            false -> syncSettings.pausedConnectors.update {
                it - identifier
            }
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Manager")
    }
}