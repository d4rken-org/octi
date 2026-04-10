package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
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
class GDriveBlobStoreHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val gDriveHub: GDriveHub,
) : BlobStoreHub {

    private val cache = ConcurrentHashMap<ConnectorId, BlobStore>()

    override val blobStores: Flow<Collection<BlobStore>> = gDriveHub.connectors
        .map { connectors ->
            connectors.mapNotNull { connector ->
                val gDriveConnector = connector as GDriveAppDataConnector
                cache.getOrPut(connector.identifier) {
                    GDriveBlobStore(gDriveConnector, connector.identifier)
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "blobStores" }
        .shareLatest(scope + dispatcherProvider.Default)

    override suspend fun owns(connectorId: ConnectorId): Boolean = gDriveHub.owns(connectorId)

    companion object {
        private val TAG = logTag("Sync", "GDrive", "BlobStore", "Hub")
    }
}
