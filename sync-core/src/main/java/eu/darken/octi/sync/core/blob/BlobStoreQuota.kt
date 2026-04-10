package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.ConnectorId

data class BlobStoreQuota(
    val connectorId: ConnectorId,
    val usedBytes: Long,
    val totalBytes: Long,
)
