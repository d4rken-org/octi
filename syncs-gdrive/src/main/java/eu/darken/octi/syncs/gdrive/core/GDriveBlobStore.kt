package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobNotFoundException
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.BlobProgressCallback
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.BlobStoreConstraints
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import eu.darken.octi.sync.core.blob.CountingSink
import eu.darken.octi.sync.core.blob.CountingSource
import okio.Sink
import okio.Source
import okio.buffer
import kotlin.time.Instant

/**
 * GDrive blob store — stores blobs under `blob-store/{deviceId}/{moduleId}/{blobKey}` in AppData.
 * No encryption (matches existing GDrive module data pattern, trusts Google account security).
 * Metadata uses Drive file fields for size/created time and a custom property for checksum.
 *
 * GDrive uses the client-side [BlobKey.id] directly as its filename, so the returned [RemoteBlobRef]
 * equals the logical key string. Callers may therefore omit `connectorRefs[gdrive]` from `SharedFile`
 * and still address blobs — but storing it keeps the contract uniform.
 */
class GDriveBlobStore(
    private val connector: GDriveAppDataConnector,
    override val connectorId: ConnectorId,
) : BlobStore {

    override suspend fun put(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
        source: Source,
        metadata: BlobMetadata,
        onProgress: BlobProgressCallback?,
    ): RemoteBlobRef {
        log(TAG, VERBOSE) { "put(key=${key.id}, device=$deviceId, module=$moduleId)" }
        val progressingSource: Source = if (onProgress != null) {
            CountingSource(source) { read ->
                onProgress(BlobProgress(bytesTransferred = read, bytesTotal = metadata.size))
            }
        } else {
            source
        }
        connector.withDrive {
            val blobStoreDir = appDataRoot.child(BLOB_STORE_DIR) ?: appDataRoot.createDir(BLOB_STORE_DIR)
            val deviceDir = blobStoreDir.child(deviceId.id) ?: blobStoreDir.createDir(deviceId.id)
            val moduleDir = deviceDir.child(moduleId.id) ?: deviceDir.createDir(moduleId.id)
            val target = moduleDir.child(key.id) ?: moduleDir.createFile(key.id)

            progressingSource.buffer().inputStream().use { input ->
                target.writeStreamed(
                    input = input,
                    sizeBytes = metadata.size,
                    mimeType = "application/octet-stream",
                    properties = buildProperties(metadata),
                )
            }
        }
        return RemoteBlobRef(key.id)
    }

    override suspend fun get(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
        remoteRef: RemoteBlobRef,
        sink: Sink,
        expectedPlaintextSize: Long,
        onProgress: BlobProgressCallback?,
    ): BlobMetadata {
        log(TAG, VERBOSE) { "get(ref=${remoteRef.value}, device=$deviceId, module=$moduleId)" }
        // [key] is unused — GDrive stores unencrypted blobs so no AAD is needed.
        return connector.withDrive {
            val file = findBlobFile(deviceId, moduleId, remoteRef) ?: throw BlobNotFoundException(remoteRef.value)
            val metadata = file.fetchBlobMetadata().toBlobMetadata(remoteRef)
            val progressingSink: Sink = if (onProgress != null) {
                CountingSink(sink) { written ->
                    onProgress(BlobProgress(bytesTransferred = written, bytesTotal = expectedPlaintextSize))
                }
            } else {
                sink
            }
            progressingSink.buffer().outputStream().use { output ->
                file.readStreamedTo(output)
            }
            metadata
        }
    }

    override suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef): BlobMetadata? {
        return connector.withDrive {
            val file = findBlobFile(deviceId, moduleId, remoteRef) ?: return@withDrive null
            file.fetchBlobMetadata().toBlobMetadata(remoteRef)
        }
    }

    override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) {
        log(TAG, VERBOSE) { "delete(ref=${remoteRef.value})" }
        connector.withDrive {
            val file = findBlobFile(deviceId, moduleId, remoteRef) ?: return@withDrive
            file.deleteAll()
        }
    }

    override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<RemoteBlobRef> {
        return connector.withDrive {
            val moduleDir = appDataRoot.child(BLOB_STORE_DIR)
                ?.child(deviceId.id)
                ?.child(moduleId.id)
                ?: return@withDrive emptySet()
            moduleDir.listFiles().map { RemoteBlobRef(it.name) }.toSet()
        }
    }

    override suspend fun getConstraints(): BlobStoreConstraints = BlobStoreConstraints(
        maxFileBytes = null,
        maxTotalBytes = null,
    )

    override suspend fun getQuota(): BlobStoreQuota? = null

    private suspend fun GDriveEnvironment.findBlobFile(
        deviceId: DeviceId,
        moduleId: ModuleId,
        remoteRef: RemoteBlobRef,
    ): com.google.api.services.drive.model.File? {
        return appDataRoot.child(BLOB_STORE_DIR)
            ?.child(deviceId.id)
            ?.child(moduleId.id)
            ?.child(remoteRef.value)
    }

    private fun buildProperties(metadata: BlobMetadata): Map<String, String> = mapOf(
        PROP_CHECKSUM to metadata.checksum,
    )

    private fun com.google.api.services.drive.model.File.toBlobMetadata(remoteRef: RemoteBlobRef): BlobMetadata {
        val sizeBytes = size?.toLong() ?: throw IllegalStateException("Missing size for blob ${remoteRef.value}")
        val createdTimeValue = createdTime?.value ?: throw IllegalStateException("Missing createdTime for blob ${remoteRef.value}")
        val checksum = properties?.get(PROP_CHECKSUM).orEmpty()

        return BlobMetadata(
            size = sizeBytes,
            createdAt = Instant.fromEpochMilliseconds(createdTimeValue),
            checksum = checksum,
        )
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "BlobStore")
        private const val BLOB_STORE_DIR = "blob-store"
        private const val PROP_CHECKSUM = "octi_blob_checksum"
    }
}
