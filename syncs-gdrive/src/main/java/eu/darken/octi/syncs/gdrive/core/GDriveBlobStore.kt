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
import eu.darken.octi.sync.core.blob.CountingSink
import eu.darken.octi.sync.core.blob.CountingSource
import eu.darken.octi.sync.core.blob.StorageStatusProvider
import eu.darken.octi.syncs.gdrive.core.GDriveEnvironment.Companion.MIME_FOLDER
import okio.Sink
import okio.Source
import okio.buffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant

/**
 * GDrive blob store — stores blobs under `blob-store/{deviceId}/{moduleId}/{blobKey}` in AppData.
 * No encryption (matches existing GDrive module data pattern, trusts Google account security).
 * Metadata uses Drive file fields for size and created time. Plaintext integrity is verified
 * via the synced `SharedFile.checksum` on the receive path, not via Drive metadata, so this
 * store does not stamp the file with a checksum property — that lets the upload run as a pure
 * stream from the supplied [Source] without needing the plaintext SHA-256 up front.
 *
 * GDrive uses the client-side [BlobKey.id] directly as its filename, so the returned [RemoteBlobRef]
 * equals the logical key string. Callers may therefore omit `connectorRefs[gdrive]` from `SharedFile`
 * and still address blobs — but storing it keeps the contract uniform.
 */
class GDriveBlobStore(
    private val connector: GDriveAppDataConnector,
    override val connectorId: ConnectorId,
    override val storageStatus: StorageStatusProvider,
) : BlobStore {

    private data class BlobFileKey(
        val deviceId: DeviceId,
        val moduleId: ModuleId,
        val remoteRef: RemoteBlobRef,
    )

    @Volatile private var blobStoreDirId: String? = null
    // Blob-store caches are intentionally local to this store. Connector token resets do not
    // invalidate Drive file IDs; stale entries self-heal through the 404 fallback paths below.
    private val blobDeviceDirCache = ConcurrentHashMap<DeviceId, String>()
    private val blobModuleDirCache = ConcurrentHashMap<Pair<DeviceId, ModuleId>, String>()
    private val blobFileIdCache = ConcurrentHashMap<BlobFileKey, String>()

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
            val target = resolveBlobFileForWrite(deviceId, moduleId, key)

            progressingSource.buffer().inputStream().use { input ->
                target.writeStreamed(
                    input = input,
                    sizeBytes = metadata.size,
                    mimeType = "application/octet-stream",
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
            val metadata = fetchBlobMetadataIfNeeded(file).toBlobMetadata(remoteRef)
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
            fetchBlobMetadataIfNeeded(file).toBlobMetadata(remoteRef)
        }
    }

    override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) {
        log(TAG, VERBOSE) { "delete(ref=${remoteRef.value})" }
        connector.withDrive {
            val cacheKey = BlobFileKey(deviceId, moduleId, remoteRef)
            blobFileIdCache[cacheKey]?.let { cachedId ->
                try {
                    cachedDriveFile(cachedId, remoteRef.value).deleteAll()
                    blobFileIdCache.remove(cacheKey)
                    return@withDrive
                } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                    if (e.statusCode != 404) throw e
                    blobFileIdCache.remove(cacheKey)
                }
            }
            val file = findBlobFile(deviceId, moduleId, remoteRef) ?: return@withDrive
            file.deleteAll()
            blobFileIdCache.remove(cacheKey)
        }
    }

    override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<RemoteBlobRef> {
        return connector.withDrive {
            val moduleDir = resolveBlobModuleDir(deviceId, moduleId, create = false) ?: return@withDrive emptySet()
            moduleDir.listFiles().map { file ->
                RemoteBlobRef(file.name).also { blobFileIdCache[BlobFileKey(deviceId, moduleId, it)] = file.id }
            }.toSet()
        }
    }

    private suspend fun GDriveEnvironment.resolveBlobFileForWrite(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
    ): com.google.api.services.drive.model.File {
        val moduleDir = resolveBlobModuleDir(deviceId, moduleId, create = true)
            ?: throw IllegalStateException("Failed to resolve blob module dir for $deviceId/$moduleId")
        val remoteRef = RemoteBlobRef(key.id)
        val cacheKey = BlobFileKey(deviceId, moduleId, remoteRef)
        blobFileIdCache[cacheKey]?.let { cachedId ->
            try {
                val cached = getFileMetadata(cachedId, BLOB_FILE_FIELDS)
                if (cached.name == key.id) return cached
                blobFileIdCache.remove(cacheKey)
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                if (e.statusCode != 404) throw e
                blobFileIdCache.remove(cacheKey)
            }
        }
        return (moduleDir.child(key.id) ?: moduleDir.createFile(key.id)).also {
            blobFileIdCache[cacheKey] = it.id
        }
    }

    private suspend fun GDriveEnvironment.findBlobFile(
        deviceId: DeviceId,
        moduleId: ModuleId,
        remoteRef: RemoteBlobRef,
    ): com.google.api.services.drive.model.File? {
        val cacheKey = BlobFileKey(deviceId, moduleId, remoteRef)
        blobFileIdCache[cacheKey]?.let { cachedId ->
            try {
                val cached = getFileMetadata(cachedId, BLOB_FILE_FIELDS)
                if (cached.name == remoteRef.value) return cached
                blobFileIdCache.remove(cacheKey)
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                if (e.statusCode != 404) throw e
                blobFileIdCache.remove(cacheKey)
            }
        }
        val moduleDir = resolveBlobModuleDir(deviceId, moduleId, create = false) ?: return null
        return moduleDir.child(remoteRef.value)?.also {
            blobFileIdCache[cacheKey] = it.id
        }
    }

    private suspend fun GDriveEnvironment.resolveBlobModuleDir(
        deviceId: DeviceId,
        moduleId: ModuleId,
        create: Boolean,
    ): com.google.api.services.drive.model.File? {
        val cacheKey = deviceId to moduleId
        blobModuleDirCache[cacheKey]?.let { return cachedDriveFolder(it, moduleId.id) }

        val deviceDir = resolveBlobDeviceDir(deviceId, create) ?: return null
        return (deviceDir.child(moduleId.id) ?: if (create) deviceDir.createDir(moduleId.id) else null)?.also {
            blobModuleDirCache[cacheKey] = it.id
        }
    }

    private suspend fun GDriveEnvironment.resolveBlobDeviceDir(
        deviceId: DeviceId,
        create: Boolean,
    ): com.google.api.services.drive.model.File? {
        blobDeviceDirCache[deviceId]?.let { return cachedDriveFolder(it, deviceId.id) }

        val blobStoreDir = resolveBlobStoreDir(create) ?: return null
        return (blobStoreDir.child(deviceId.id) ?: if (create) blobStoreDir.createDir(deviceId.id) else null)?.also {
            blobDeviceDirCache[deviceId] = it.id
        }
    }

    private suspend fun GDriveEnvironment.resolveBlobStoreDir(
        create: Boolean,
    ): com.google.api.services.drive.model.File? {
        blobStoreDirId?.let { return cachedDriveFolder(it, BLOB_STORE_DIR) }

        val root = appDataRoot
        return (root.child(BLOB_STORE_DIR) ?: if (create) root.createDir(BLOB_STORE_DIR) else null)?.also {
            blobStoreDirId = it.id
        }
    }

    private fun cachedDriveFile(fileId: String, name: String): com.google.api.services.drive.model.File {
        return com.google.api.services.drive.model.File().apply {
            id = fileId
            this.name = name
        }
    }

    private fun cachedDriveFolder(fileId: String, name: String): com.google.api.services.drive.model.File {
        return cachedDriveFile(fileId, name).apply {
            mimeType = MIME_FOLDER
        }
    }

    private suspend fun GDriveEnvironment.fetchBlobMetadataIfNeeded(
        file: com.google.api.services.drive.model.File,
    ): com.google.api.services.drive.model.File {
        return if (file.createdTime != null) file else file.fetchBlobMetadata()
    }

    private fun com.google.api.services.drive.model.File.toBlobMetadata(remoteRef: RemoteBlobRef): BlobMetadata {
        val sizeBytes = getSize() ?: throw IllegalStateException("Missing size for blob ${remoteRef.value}")
        val createdTimeValue = createdTime?.value ?: throw IllegalStateException("Missing createdTime for blob ${remoteRef.value}")
        // Checksum is intentionally empty — see class doc. Receivers verify against
        // SharedFile.checksum from the synced module document instead.
        return BlobMetadata(
            size = sizeBytes,
            createdAt = Instant.fromEpochMilliseconds(createdTimeValue),
            checksum = "",
        )
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "BlobStore")
        private const val BLOB_STORE_DIR = "blob-store"
        private const val BLOB_FILE_FIELDS = "id,name,mimeType,parents,createdTime,modifiedTime,size"
    }
}
