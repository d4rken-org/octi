package eu.darken.octi.sync.core.blob

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import okio.Sink
import okio.Source

interface BlobStore {
    val connectorId: ConnectorId

    /**
     * Upload a blob from [source].
     * @param key stable client-side logical identity (also used as AAD input for encryption where applicable).
     * @param onProgress optional callback fired during the upload. Always plaintext-bytes per the
     *        [BlobProgress] contract: encrypting stores scale their on-the-wire ciphertext
     *        progress to plaintext-equivalent before reporting. Always called from the IO dispatcher.
     * @return the connector-specific remote reference that locates the uploaded blob.
     *         The caller persists this in `SharedFile.connectorRefs[connectorId]` so future reads
     *         address the blob via [get] / [delete] without needing a local key cache.
     */
    suspend fun put(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
        source: Source,
        metadata: BlobMetadata,
        onProgress: BlobProgressCallback? = null,
    ): RemoteBlobRef

    /**
     * Download a blob to [sink].
     * @param key stable client-side logical identity — required because encrypting stores bind AAD
     *        to `(deviceId, moduleId, blobKey)`. GDrive ignores [key] but it must still be supplied
     *        so callers can use [BlobStore] polymorphically.
     * @param remoteRef the value returned from [put], read back from `SharedFile.connectorRefs`.
     * @param expectedPlaintextSize the original sender-side plaintext size. The server cannot know
     *        this in an E2EE setting, so callers must supply it from the synced module metadata
     *        (e.g. `FileShareInfo.SharedFile.size`). Used as the [BlobProgress.bytesTotal] for
     *        progress reporting; pass `0` when no progress is needed.
     * @param onProgress optional callback fired as plaintext bytes arrive at [sink], per the
     *        [BlobProgress] contract.
     * @return metadata of the actually-downloaded blob. Note: for OctiServer the returned
     *         [BlobMetadata.size] is the **ciphertext** size (the server cannot see plaintext);
     *         use [expectedPlaintextSize] / synced module metadata for plaintext context.
     */
    suspend fun get(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
        remoteRef: RemoteBlobRef,
        sink: Sink,
        expectedPlaintextSize: Long,
        onProgress: BlobProgressCallback? = null,
    ): BlobMetadata

    /**
     * Get blob metadata without downloading content. Returns null if the blob is not present.
     *
     * Note: for OctiServer, [BlobMetadata.size] is the ciphertext byte count; the server has no
     * way to know plaintext size in an E2EE setting. Callers wanting plaintext size must use
     * the synced module metadata.
     */
    suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef): BlobMetadata?

    /**
     * Delete a single blob identified by its remote reference. Silent no-op if absent.
     */
    suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef)

    /**
     * List all remote references currently present for a device+module namespace.
     * Used for listings where remote refs happen to match logical keys (GDrive) or for diagnostic listings.
     */
    suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<RemoteBlobRef>

    /**
     * Connector-specific constraints/caps.
     */
    suspend fun getConstraints(): BlobStoreConstraints

    /**
     * Connector-specific quota, if meaningful.
     */
    suspend fun getQuota(): BlobStoreQuota?
}
