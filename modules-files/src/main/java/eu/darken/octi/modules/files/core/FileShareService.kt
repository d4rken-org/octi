package eu.darken.octi.modules.files.core

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobChecksumMismatchException
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobMetadata
import okio.FileSystem
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Singleton
class FileShareService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val fileShareHandler: FileShareHandler,
    private val fileShareSettings: FileShareSettings,
    private val blobManager: BlobManager,
    private val syncSettings: SyncSettings,
) {

    sealed class ShareResult {
        data object Success : ShareResult()
        data class PartialMirror(
            val successful: Set<ConnectorId>,
            val failures: Map<ConnectorId, Throwable>,
        ) : ShareResult()

        data class AllConnectorsFailed(val errors: Map<ConnectorId, Throwable>) : ShareResult()
        data object NoEligibleConnectors : ShareResult()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object NotAvailable : SaveResult()
        data class Failed(val cause: Throwable) : SaveResult()
    }

    sealed class DeleteResult {
        data object Deleted : DeleteResult()
        data object NotFound : DeleteResult()
        data class Partial(val remainingConnectors: Set<String>) : DeleteResult()
    }

    private val stagingDir: File
        get() = BlobCacheDirs.dir(context, BlobCacheDirs.STAGING)

    private val downloadDir: File
        get() = BlobCacheDirs.dir(context, BlobCacheDirs.DOWNLOAD)

    suspend fun shareFile(uri: android.net.Uri): ShareResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "shareFile(uri=$uri)" }
        val contentResolver = context.contentResolver

        // 1. Query metadata
        val (displayName, mimeType) = queryFileMetadata(contentResolver, uri)

        // 2. Stage to temp file + compute checksum + size
        val stagedFile = BlobCacheDirs.tempFile(stagingDir)
        val stagedPath = stagedFile.absolutePath.toPath()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L

            contentResolver.openInputStream(uri)?.use { input ->
                stagedFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        size += read
                    }
                }
            } ?: run {
                log(TAG, WARN) { "shareFile(): Failed to open input stream for URI" }
                return@withContext ShareResult.AllConnectorsFailed(emptyMap())
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            log(TAG, VERBOSE) { "Staged file: name=$displayName, size=$size, checksum=$checksum" }

            // 3. Generate blob key
            val blobKey = BlobKey(UUID.randomUUID().toString())
            val metadata = BlobMetadata(
                size = size,
                createdAt = Clock.System.now(),
                checksum = checksum,
            )

            // 4. Upload to blob stores
            val putResult = blobManager.put(
                deviceId = syncSettings.deviceId,
                moduleId = FileShareModule.MODULE_ID,
                blobKey = blobKey,
                openSource = { FileSystem.SYSTEM.source(stagedPath) },
                metadata = metadata,
            )

            if (putResult.successful.isEmpty()) {
                log(TAG, WARN) { "shareFile(): All connectors failed" }
                return@withContext if (putResult.perConnectorErrors.isEmpty()) {
                    ShareResult.NoEligibleConnectors
                } else {
                    ShareResult.AllConnectorsFailed(putResult.perConnectorErrors)
                }
            }

            // 5. Create metadata entry
            val sharedFile = FileShareInfo.SharedFile(
                name = displayName,
                mimeType = mimeType,
                size = size,
                blobKey = blobKey.id,
                checksum = checksum,
                sharedAt = Clock.System.now(),
                expiresAt = Clock.System.now() + DEFAULT_EXPIRY,
                availableOn = putResult.successful.map { it.idString }.toSet(),
                connectorRefs = putResult.remoteRefs.mapKeys { (connectorId, _) -> connectorId.idString },
            )
            fileShareHandler.upsertFile(sharedFile)

            if (putResult.perConnectorErrors.isEmpty()) {
                ShareResult.Success
            } else {
                ShareResult.PartialMirror(putResult.successful, putResult.perConnectorErrors)
            }
        } finally {
            stagedFile.delete()
        }
    }

    suspend fun saveFile(
        sharedFile: FileShareInfo.SharedFile,
        ownerDeviceId: DeviceId,
        destinationUri: android.net.Uri,
    ): SaveResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "saveFile(blobKey=${sharedFile.blobKey}, owner=$ownerDeviceId)" }

        val configuredById = blobManager.configuredConnectorsByIdString()
        // A connector listed in `availableOn` but missing from `connectorRefs` is treated as
        // unreachable. `shareFile` always writes both fields together from `PutResult.remoteRefs`,
        // so a missing entry means the ref was never recorded (or lost) — there is no safe
        // fallback since OctiServer's ref differs from the logical BlobKey.
        val candidates: Map<ConnectorId, RemoteBlobRef> = sharedFile.availableOn
            .mapNotNull { idStr ->
                val connectorId = configuredById[idStr] ?: return@mapNotNull null
                val ref = sharedFile.connectorRefs[idStr] ?: return@mapNotNull null
                connectorId to ref
            }
            .toMap()

        if (candidates.isEmpty()) {
            log(TAG, WARN) { "saveFile(): No available connectors overlap with configured+resolvable" }
            return@withContext SaveResult.NotAvailable
        }

        val downloadFile = BlobCacheDirs.tempFile(downloadDir)
        val downloadPath = downloadFile.absolutePath.toPath()
        try {
            blobManager.get(
                deviceId = ownerDeviceId,
                moduleId = FileShareModule.MODULE_ID,
                blobKey = BlobKey(sharedFile.blobKey),
                candidates = candidates,
                openSink = { FileSystem.SYSTEM.sink(downloadPath) },
            )

            // Verify checksum against synced metadata
            val digest = MessageDigest.getInstance("SHA-256")
            downloadFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
            if (sharedFile.checksum.isEmpty()) {
                log(TAG, WARN) { "saveFile(): No checksum available for ${sharedFile.blobKey}, skipping integrity check" }
            } else if (actualChecksum != sharedFile.checksum) {
                return@withContext SaveResult.Failed(
                    BlobChecksumMismatchException(
                        blobKey = BlobKey(sharedFile.blobKey),
                        expected = sharedFile.checksum,
                        actual = actualChecksum,
                    )
                )
            }

            // Copy to SAF destination
            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext SaveResult.Failed(IllegalStateException("Failed to open destination output stream"))

            outputStream.use { output ->
                downloadFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            SaveResult.Success
        } catch (e: Exception) {
            log(TAG, ERROR) { "saveFile() failed: ${e.asLog()}" }
            SaveResult.Failed(e)
        } finally {
            downloadFile.delete()
        }
    }

    suspend fun deleteOwnFile(blobKey: String): DeleteResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "deleteOwnFile(blobKey=$blobKey)" }
        val currentState = fileShareHandler.currentOwn()
        val sharedFile = currentState.files.find { it.blobKey == blobKey } ?: run {
            fileShareSettings.pendingDeletes.update { it - blobKey }
            return@withContext DeleteResult.NotFound
        }

        val configuredById = blobManager.configuredConnectorsByIdString()
        val targets: Map<ConnectorId, RemoteBlobRef> = sharedFile.availableOn
            .mapNotNull { idStr ->
                val connectorId = configuredById[idStr] ?: return@mapNotNull null
                val ref = sharedFile.connectorRefs[idStr] ?: return@mapNotNull null
                connectorId to ref
            }
            .toMap()

        if (targets.isEmpty()) {
            val tombstone = PendingDelete(
                blobKey = blobKey,
                remainingConnectors = sharedFile.availableOn,
                createdAt = Clock.System.now(),
            )
            fileShareSettings.pendingDeletes.update { it + (blobKey to tombstone) }
            return@withContext DeleteResult.Partial(sharedFile.availableOn)
        }

        val successfulDeletes = blobManager.delete(
            deviceId = syncSettings.deviceId,
            moduleId = FileShareModule.MODULE_ID,
            blobKey = BlobKey(blobKey),
            targets = targets,
        )

        val deletedIds = successfulDeletes.map { it.idString }.toSet()
        val newAvailableOn = sharedFile.availableOn - deletedIds
        val newConnectorRefs = sharedFile.connectorRefs - deletedIds
        if (newAvailableOn.isEmpty()) {
            fileShareHandler.removeFile(blobKey)
            fileShareSettings.pendingDeletes.update { it - blobKey }
            DeleteResult.Deleted
        } else {
            fileShareHandler.updateLocations(blobKey, newAvailableOn, newConnectorRefs)
            val tombstone = PendingDelete(
                blobKey = blobKey,
                remainingConnectors = newAvailableOn,
                createdAt = Clock.System.now(),
            )
            fileShareSettings.pendingDeletes.update { it + (blobKey to tombstone) }
            DeleteResult.Partial(newAvailableOn)
        }
    }

    private fun queryFileMetadata(contentResolver: ContentResolver, uri: android.net.Uri): Pair<String, String> {
        var name = "shared-${Clock.System.now().toEpochMilliseconds()}"
        var mimeType = "application/octet-stream"

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                        cursor.getString(it)?.takeIf { s -> s.isNotBlank() }?.let { n -> name = n }
                    }
                }
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to query file name: ${e.message}" }
        }

        try {
            contentResolver.getType(uri)?.takeIf { it.isNotBlank() }?.let { mimeType = it }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to get MIME type: ${e.message}" }
        }

        return name to mimeType
    }

    companion object {
        private val TAG = logTag("Module", "Files", "Service")
        private val DEFAULT_EXPIRY = 48.hours
    }
}
