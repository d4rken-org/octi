package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.provider.gdrive.GDriveHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncOptions: SyncOptions,
    private val gDriveHub: GDriveHub,
) {

    val hubs: Flow<List<Sync.Hub>> = flow {
        emit(listOf(gDriveHub))
        awaitCancellation()
    }
        .setupCommonEventHandlers(TAG) { "hubs" }
        .shareLatest(scope + dispatcherProvider.IO)

    val connectors: Flow<List<Sync.Connector>> = hubs
        .flatMapLatest { hs ->
            combine(hs.map { it.connectors }) { it.toList().flatten() }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.IO)

    val states: Flow<Collection<Sync.Connector.State>> = connectors
        .flatMapLatest { hs ->
            if (hs.isEmpty()) flowOf(emptyList())
            else combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }
        .shareLatest(scope + dispatcherProvider.IO)

    val data: Flow<Collection<Sync.Read>> = connectors
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

    suspend fun write(toWrite: Sync.Module) {
        val start = System.currentTimeMillis()
        log(TAG) { "write(data=$toWrite)..." }
        connectors.first().forEach {
            it.write(
                SyncWriteContainer(
                    userId = syncOptions.syncUserId,
                    deviceId = syncOptions.deviceId,
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