package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val accRepo: GDriveAccRepo,
    private val connectorFactory: GDriveAppDataConnector.Factory,
) : Sync.Hub {

    private val _connectors = accRepo.accounts
        .mapLatest { acc ->
            acc.map { connectorFactory.create(it) }
        }
        .shareLatest(TAG, scope + dispatcherProvider.IO)

    val connectors: Flow<Collection<Sync.Connector>> = _connectors

    val data: Flow<Collection<Sync.Data>> = _connectors
        .flatMapLatest { connectors ->
            combine(connectors.map { it.state }) { it.toList() }
        }
        .shareLatest(TAG, scope + dispatcherProvider.IO)

    companion object {
        private val TAG = logTag("Sync", "GDrive", "Hub")
    }
}