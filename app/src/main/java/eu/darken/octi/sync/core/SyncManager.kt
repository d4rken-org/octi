package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.syncs.gdrive.core.GDriveAppDataConnector
import eu.darken.octi.syncs.gdrive.core.GDriveHub
import eu.darken.octi.syncs.jserver.core.JServerConnector
import eu.darken.octi.syncs.jserver.core.JServerHub
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
    private val gDriveHub: GDriveHub,
    private val jServerHub: JServerHub,
) {

    private val disabledConnectors = MutableStateFlow(emptySet<SyncConnector>())

    val hubs: Flow<List<SyncHub>> = flow {
        emit(listOf(gDriveHub, jServerHub))
        awaitCancellation()
    }
        .setupCommonEventHandlers(TAG) { "hubs" }
        .shareLatest(scope + dispatcherProvider.Default)

    val connectors: Flow<List<SyncConnector>> = combine(
        hubs.flatMapLatest { hs -> combine(hs.map { it.connectors }) { it.toList().flatten() } },
        disabledConnectors
    ) { connectors, disabledConnectors ->
        connectors.filter { !disabledConnectors.contains(it) }
    }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.Default)

    val states: Flow<Collection<SyncConnector.State>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }
        .shareLatest(scope + dispatcherProvider.Default)

    val data: Flow<Collection<SyncRead>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.data }) { it.toList() }
        }
        .map { reads ->
            reads.filterNotNull()
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }
        .shareLatest(scope + dispatcherProvider.Default)

    suspend fun sync() {
        log(TAG) { "syncAll()" }
        connectors.first().forEach {
            it.read()
        }
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

    private suspend fun getConnectorById(identifier: SyncConnector.Identifier): SyncConnector {
        return connectors.map { connecs -> connecs.single { it.identifier == identifier } }.first()
    }

    suspend fun wipe(identifier: SyncConnector.Identifier) = withContext(NonCancellable) {
        log(TAG) { "wipe(identifier=$identifier)" }
        getConnectorById(identifier).deleteAll()
        log(TAG) { "wipe(identifier=$identifier) done" }
    }

    suspend fun disconnect(identifier: SyncConnector.Identifier, wipe: Boolean = false) = withContext(NonCancellable) {
        log(TAG) { "disconnect(identifier=$identifier, wipe=$wipe)" }

        val connector = getConnectorById(identifier)

        disabledConnectors.value = disabledConnectors.value + connector
        try {
            if (wipe) connector.deleteAll()
            else connector.deleteDevice(syncSettings.deviceId)

            when (connector) {
                is GDriveAppDataConnector -> {
                    gDriveHub.accountRepo.remove(connector.account.id)
                }
                is JServerConnector -> {
                    jServerHub.accountRepo.remove(connector.credentials.accountId)
                }
            }
        } finally {
            disabledConnectors.value = disabledConnectors.value - connector
        }

        log(TAG) { "disconnect(connector=$connector, wipe=$wipe) done" }
    }

    companion object {
        private val TAG = logTag("Sync", "Repo")
    }
}