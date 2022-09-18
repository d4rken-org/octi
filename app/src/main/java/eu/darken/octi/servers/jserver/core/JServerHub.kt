package eu.darken.octi.servers.jserver.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.SyncConnector
import eu.darken.octi.sync.core.SyncHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JServerHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    accRepo: JServerAccountRepo,
    private val connectorFactory: JServerConnector.Factory,
) : SyncHub {

    private val _connectors = accRepo.accounts
        .mapLatest { acc ->
            acc.map { connectorFactory.create(it) }
        }
        .setupCommonEventHandlers(TAG) { "connectors" }
        .shareLatest(scope + dispatcherProvider.IO)

    override val connectors: Flow<Collection<SyncConnector>> = _connectors

    companion object {
        private val TAG = logTag("Sync", "JServer", "Hub")
    }
}