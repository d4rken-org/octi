package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.servers.gdrive.core.GDriveHub
import eu.darken.octi.servers.jserver.core.JServerHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
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

    val hubs: Flow<List<SyncHub>> = flow {
        emit(listOf(gDriveHub, jServerHub))
        awaitCancellation()
    }
        .setupCommonEventHandlers(TAG) { "hubs" }
        .shareLatest(scope + dispatcherProvider.IO)

    val connectors: Flow<List<SyncConnector>> = hubs
        .flatMapLatest { hs ->
            combine(hs.map { it.connectors }) { it.toList().flatten() }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.IO)

    val states: Flow<Collection<SyncConnector.State>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }
        .shareLatest(scope + dispatcherProvider.IO)

    val data: Flow<Collection<SyncRead>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.data }) { it.toList() }
        }
        .map { reads ->
            reads.filterNotNull()
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }
        .shareLatest(scope + dispatcherProvider.IO)

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

    companion object {
        private val TAG = logTag("Sync", "Repo")
    }
}