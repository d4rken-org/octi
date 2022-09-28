package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.cache.SyncCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val syncCache: SyncCache,
    private val connectorHubs: Set<@JvmSuppressWildcards ConnectorHub>,
) {

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

    val data: Flow<Collection<SyncRead.Device>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) {
                flowOf(emptyList())
            } else {
                val cons = hs.map { con -> con.data.map { con.identifier to it } }
                combine(cons) { it.toSet() }
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

    fun start() {
        log(TAG) { "start()" }
        // NOOP?
    }

    suspend fun sync(options: SyncOptions = SyncOptions()) {
        log(TAG) { "sync(options=$options)" }
        val syncJobs = connectors.first().map {
            scope.launch {
                sync(it.identifier, options = options)
            }
        }
        syncJobs.joinAll()
    }

    suspend fun sync(connectorId: ConnectorId, options: SyncOptions = SyncOptions()) {
        log(TAG) { "sync(id=$connectorId,options=$options)" }
        val connector = connectors.first().single { it.identifier == connectorId }
        connector.sync(options)
    }

    suspend fun write(toWrite: SyncWrite.Device.Module) {
        val start = System.currentTimeMillis()
        log(TAG) { "write(data=$toWrite)..." }
        connectors.first().forEach {
            it.write(
                SyncWriteContainer(
                    deviceId = syncSettings.deviceId,
                    modules = listOf(
                        toWrite
                    )
                )
            )
        }

        log(TAG) { "write(data=$toWrite) done (${System.currentTimeMillis() - start}ms)" }
    }

    suspend fun wipe(identifier: ConnectorId) = withContext(NonCancellable) {
        log(TAG) { "wipe(identifier=$identifier)" }
        getConnectorById<SyncConnector>(identifier).first().deleteAll()
        log(TAG) { "wipe(identifier=$identifier) done" }
    }

    suspend fun disconnect(identifier: ConnectorId, wipe: Boolean = false) = withContext(NonCancellable) {
        log(TAG) { "disconnect(identifier=$identifier, wipe=$wipe)" }

        val connector = getConnectorById<SyncConnector>(identifier).first()

        disabledConnectors.value = disabledConnectors.value + connector
        try {
            try {
                if (wipe) connector.deleteAll()
                else connector.deleteDevice(syncSettings.deviceId)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Wiping data pre deletion failed: ${e.asLog()}" }
            }

            hubs.first().filter { it.owns(identifier) }.forEach {
                it.remove(identifier)
            }
        } finally {
            disabledConnectors.value = disabledConnectors.value - connector
        }

        log(TAG) { "disconnect(connector=$connector, wipe=$wipe) done" }
    }

    companion object {
        private val TAG = logTag("Sync", "Manager")
    }
}