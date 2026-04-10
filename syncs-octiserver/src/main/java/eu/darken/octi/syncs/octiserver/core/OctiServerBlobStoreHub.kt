package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.BlobStoreHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OctiServerBlobStoreHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val octiServerHub: OctiServerHub,
    private val blobStoreFactory: OctiServerBlobStore.Factory,
    private val endpointFactory: OctiServerEndpoint.Factory,
) : BlobStoreHub {

    private val cache = ConcurrentHashMap<ConnectorId, BlobStore>()

    override val blobStores: Flow<Collection<BlobStore>> = octiServerHub.connectors
        .map { connectors ->
            connectors.mapNotNull { connector ->
                val id = connector.identifier
                cache.getOrPut(id) {
                    val octiConnector = connector as? OctiServerConnector ?: return@mapNotNull null
                    try {
                        val endpoint = endpointFactory.create(octiConnector.credentials.serverAdress).also {
                            it.setCredentials(octiConnector.credentials)
                        }
                        blobStoreFactory.create(octiConnector.credentials, endpoint)
                    } catch (e: IllegalArgumentException) {
                        // Legacy SIV keyset — skip this connector for blob operations
                        log(TAG, INFO) { "Skipping blob store for ${id}: ${e.message}" }
                        return@mapNotNull null
                    }
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "blobStores" }
        .shareLatest(scope + dispatcherProvider.Default)

    override suspend fun owns(connectorId: ConnectorId): Boolean = octiServerHub.owns(connectorId)

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "BlobStore", "Hub")
    }
}
