package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.provider.gdrive.GDriveHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncOptions: SyncOptions,
    private val gDriveHub: GDriveHub,
) {

    val hubs: Flow<List<Sync.Hub>> = flowOf(listOf(gDriveHub))
        .setupCommonEventHandlers(TAG) { "hubs" }

    val connectors: Flow<List<Sync.Connector>> = hubs
        .flatMapLatest { hs ->
            combine(hs.map { it.connectors }) { it.toList().flatten() }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }

        .shareLatest(TAG, scope + dispatcherProvider.IO)

    val syncStates: Flow<Collection<Sync.Connector.State>> = connectors
        .flatMapLatest { hs ->
            combine(hs.map { it.state }) { it.toList() }
        }
        .setupCommonEventHandlers(TAG) { "syncStates" }

        .shareLatest(TAG, scope + dispatcherProvider.IO)

    val syncData: Flow<Collection<Sync.Read>> = syncStates
        .map { sts ->
            sts.mapNotNull { it.data }
        }
        .setupCommonEventHandlers(TAG) { "syncData" }
        .shareLatest(TAG, scope + dispatcherProvider.IO)

    suspend fun syncAll() {
        log(TAG) { "syncAll()" }
        connectors.first().forEach {
            it.sync()
        }
    }

    companion object {
        private val TAG = logTag("Sync", "Repo")
    }
}