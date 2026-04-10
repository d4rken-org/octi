package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobNotFoundException
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.BlobStoreConstraints
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import okio.Path
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.time.Instant

/**
 * GDrive blob store — stores blobs under `blob-store/{deviceId}/{moduleId}/{blobKey}` in AppData.
 * No encryption (matches existing GDrive module data pattern, trusts Google account security).
 * Metadata is stored via GDrive custom file properties.
 */
class GDriveBlobStore(
    private val connector: GDriveAppDataConnector,
    override val connectorId: ConnectorId,
) : BlobStore {

    override suspend fun put(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, payloadFile: Path, metadata: BlobMetadata) {
        log(TAG, VERBOSE) { "put(key=${key.id}, device=$deviceId, module=$moduleId)" }
        connector.withDrive {
            val blobStoreDir = appDataRoot.child(BLOB_STORE_DIR) ?: appDataRoot.createDir(BLOB_STORE_DIR)
            val deviceDir = blobStoreDir.child(deviceId.id) ?: blobStoreDir.createDir(deviceId.id)
            val moduleDir = deviceDir.child(moduleId.id) ?: deviceDir.createDir(moduleId.id)

            val existing = moduleDir.child(key.id)
            if (existing != null) {
                // Overwrite existing blob
                FileInputStream(payloadFile.toFile()).use { input ->
                    existing.writeStreamed(
                        input = input,
                        sizeBytes = metadata.size,
                        mimeType = "application/octet-stream",
                        properties = buildProperties(metadata),
                    )
                }
            } else {
                val newFile = moduleDir.createFile(key.id)
                FileInputStream(payloadFile.toFile()).use { input ->
                    newFile.writeStreamed(
                        input = input,
                        sizeBytes = metadata.size,
                        mimeType = "application/octet-stream",
                        properties = buildProperties(metadata),
                    )
                }
            }
        }
    }

    override suspend fun get(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, destinationFile: Path): BlobMetadata {
        log(TAG, VERBOSE) { "get(key=${key.id}, device=$deviceId, module=$moduleId)" }
        return connector.withDrive {
            val file = findBlobFile(deviceId, moduleId, key) ?: throw BlobNotFoundException(key)
            FileOutputStream(destinationFile.toFile()).use { output ->
                file.readStreamedTo(output)
            }
            val props = file.fetchProperties() ?: emptyMap()
            BlobMetadata(
                size = props[PROP_SIZE]?.toLongOrNull() ?: 0,
                createdAt = props[PROP_CREATED_AT]?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
                    ?: kotlin.time.Clock.System.now(),
                checksum = props[PROP_CHECKSUM] ?: "",
            )
        }
    }

    override suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey): BlobMetadata? {
        return connector.withDrive {
            val file = findBlobFile(deviceId, moduleId, key) ?: return@withDrive null
            val props = file.fetchProperties() ?: emptyMap()
            BlobMetadata(
                size = props[PROP_SIZE]?.toLongOrNull() ?: 0,
                createdAt = props[PROP_CREATED_AT]?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
                    ?: kotlin.time.Clock.System.now(),
                checksum = props[PROP_CHECKSUM] ?: "",
            )
        }
    }

    override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey) {
        log(TAG, VERBOSE) { "delete(key=${key.id})" }
        connector.withDrive {
            val file = findBlobFile(deviceId, moduleId, key) ?: return@withDrive
            file.deleteAll()
        }
    }

    override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<BlobKey> {
        return connector.withDrive {
            val moduleDir = appDataRoot.child(BLOB_STORE_DIR)
                ?.child(deviceId.id)
                ?.child(moduleId.id)
                ?: return@withDrive emptySet()
            moduleDir.listFiles().map { BlobKey(it.name) }.toSet()
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
        key: BlobKey,
    ): com.google.api.services.drive.model.File? {
        return appDataRoot.child(BLOB_STORE_DIR)
            ?.child(deviceId.id)
            ?.child(moduleId.id)
            ?.child(key.id)
    }

    private fun buildProperties(metadata: BlobMetadata): Map<String, String> = mapOf(
        PROP_SIZE to metadata.size.toString(),
        PROP_CREATED_AT to metadata.createdAt.toEpochMilliseconds().toString(),
        PROP_CHECKSUM to metadata.checksum,
    )

    companion object {
        private val TAG = logTag("Sync", "GDrive", "BlobStore")
        private const val BLOB_STORE_DIR = "blob-store"
        private const val PROP_SIZE = "octi_blob_size"
        private const val PROP_CREATED_AT = "octi_blob_created_at"
        private const val PROP_CHECKSUM = "octi_blob_checksum"
    }
}
