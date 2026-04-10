package eu.darken.octi.sync.core.blob

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import okio.Path

interface BlobStore {
    val connectorId: ConnectorId

    /**
     * Upload a blob from [payloadFile] (streaming, scoped to device + module).
     * @param payloadFile caller-owned temp file with the plaintext (or pre-encrypted for backends that handle their own crypto)
     */
    suspend fun put(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, payloadFile: Path, metadata: BlobMetadata)

    /**
     * Download a blob to [destinationFile] (streaming).
     * @return metadata of the actually-downloaded blob
     */
    suspend fun get(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, destinationFile: Path): BlobMetadata

    /**
     * Get blob metadata without downloading content.
     */
    suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey): BlobMetadata?

    /**
     * Delete a single blob.
     */
    suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey)

    /**
     * List all blob keys for a device+module.
     */
    suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<BlobKey>

    /**
     * Connector-specific constraints/caps.
     */
    suspend fun getConstraints(): BlobStoreConstraints

    /**
     * Connector-specific quota, if meaningful.
     */
    suspend fun getQuota(): BlobStoreQuota?
}
