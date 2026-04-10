package eu.darken.octi.syncs.octiserver.core

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.BlobStoreConstraints
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import eu.darken.octi.sync.core.blob.StreamingPayloadCipher
import eu.darken.octi.sync.core.encryption.EncryptionMode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File
import java.util.UUID

class OctiServerBlobStore @AssistedInject constructor(
    @Assisted private val credentials: OctiServer.Credentials,
    @Assisted private val endpoint: OctiServerEndpoint,
    @ApplicationContext private val context: Context,
) : BlobStore {

    init {
        require(EncryptionMode.fromTypeString(credentials.encryptionKeyset.type) != EncryptionMode.AES256_SIV) {
            "Legacy AES256_SIV keysets are not supported for blob storage"
        }
    }

    private val cipher by lazy { StreamingPayloadCipher(credentials.encryptionKeyset) }

    override val connectorId: ConnectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = credentials.serverAdress.domain,
        account = credentials.accountId.id,
    )

    private val blobCacheDir: File
        get() = File(context.cacheDir, "blob-enc").also { it.mkdirs() }

    private fun buildAad(deviceId: DeviceId, moduleId: ModuleId, blobKey: BlobKey): ByteArray =
        "${deviceId.id}:${moduleId.id}:${blobKey.id}".toByteArray()

    override suspend fun put(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, payloadFile: Path, metadata: BlobMetadata) {
        log(TAG, VERBOSE) { "put(key=${key.id}, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        val cipherFile = File(blobCacheDir, "${UUID.randomUUID()}.tmp")
        val cipherPath = cipherFile.absolutePath.toPath()
        try {
            cipher.encryptToFile(payloadFile, cipherPath, buildAad(deviceId, moduleId, key))

            val body = cipherFile.asRequestBody("application/octet-stream".toMediaType())

            endpoint.putBlob(
                connectorId = connectorId,
                deviceId = deviceId,
                moduleId = moduleId,
                blobKey = key,
                body = body,
                sizeBytes = metadata.size,
                checksum = metadata.checksum,
            )
        } finally {
            cipherFile.delete()
        }
    }

    override suspend fun get(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey, destinationFile: Path): BlobMetadata {
        log(TAG, VERBOSE) { "get(key=${key.id}, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }
        val cipherFile = File(blobCacheDir, "${UUID.randomUUID()}.tmp")
        val cipherPath = cipherFile.absolutePath.toPath()
        try {
            val responseBody = endpoint.getBlob(deviceId, moduleId, key)
                ?: throw eu.darken.octi.sync.core.blob.BlobNotFoundException(key)

            responseBody.use { body ->
                FileSystem.SYSTEM.sink(cipherPath).buffer().use { sink ->
                    sink.writeAll(body.source())
                }
            }

            cipher.decryptToFile(cipherPath, destinationFile, buildAad(deviceId, moduleId, key))

            val plainSize = FileSystem.SYSTEM.metadata(destinationFile).size ?: 0
            return BlobMetadata(
                size = plainSize,
                createdAt = kotlin.time.Clock.System.now(),
                checksum = "",
            )
        } finally {
            cipherFile.delete()
        }
    }

    override suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey): BlobMetadata? {
        val entries = endpoint.listBlobs(deviceId, moduleId)
        val entry = entries.find { it.key == key.id } ?: return null
        return BlobMetadata(
            size = entry.size,
            createdAt = entry.createdAt,
            checksum = entry.checksum,
        )
    }

    override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, key: BlobKey) {
        log(TAG, VERBOSE) { "delete(key=${key.id})" }
        endpoint.deleteBlob(deviceId, moduleId, key)
    }

    override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<BlobKey> {
        return endpoint.listBlobs(deviceId, moduleId).map { BlobKey(it.key) }.toSet()
    }

    override suspend fun getConstraints(): BlobStoreConstraints = BlobStoreConstraints(
        maxFileBytes = MAX_FILE_BYTES,
        maxTotalBytes = MAX_TOTAL_BYTES,
    )

    override suspend fun getQuota(): BlobStoreQuota? {
        return try {
            endpoint.getBlobQuota()
        } catch (e: Exception) {
            log(TAG) { "getBlobQuota() failed: ${e.message}" }
            null
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(credentials: OctiServer.Credentials, endpoint: OctiServerEndpoint): OctiServerBlobStore
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "BlobStore")
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
    }
}
