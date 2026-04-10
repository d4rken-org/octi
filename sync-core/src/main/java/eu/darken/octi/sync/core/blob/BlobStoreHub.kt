package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.ConnectorId
import kotlinx.coroutines.flow.Flow

interface BlobStoreHub {
    val blobStores: Flow<Collection<BlobStore>>

    suspend fun owns(connectorId: ConnectorId): Boolean
}
