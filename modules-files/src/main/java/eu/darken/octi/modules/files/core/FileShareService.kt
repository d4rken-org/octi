package eu.darken.octi.modules.files.core

import android.content.ContentResolver
import android.content.Context
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobChecksumMismatchException
import eu.darken.octi.sync.core.blob.BlobFileTooLargeException
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.BlobQuotaExceededException
import eu.darken.octi.sync.core.blob.BlobServerStorageLowException
import eu.darken.octi.sync.core.blob.StorageStatusManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import okio.FileSystem
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
    private val fileShareSync: FileShareSync,
    private val syncManager: SyncManager,
    private val blobManager: BlobManager,
    private val blobMaintenance: BlobMaintenance,
    private val storageStatusManager: StorageStatusManager,
    private val syncSettings: SyncSettings,
    private val blobCacheDirs: BlobCacheDirs,
) {

    sealed class ShareResult {
        data object Success : ShareResult()
        data class PartialMirror(
            val successful: Set<ConnectorId>,
            val failures: Map<ConnectorId, Throwable>,
        ) : ShareResult()

        data class AllConnectorsFailed(val errors: Map<ConnectorId, Throwable>) : ShareResult()
        data class FileTooLarge(val requestedBytes: Long, val maxBytes: Long) : ShareResult()
        data object NoEligibleConnectors : ShareResult()
        data object Cancelled : ShareResult()
        /** Every reachable connector reported `BlobServerStorageLowException`. */
        data object ServerStorageLow : ShareResult()
        /** Every reachable connector reported `BlobQuotaExceededException`. */
        data object AccountQuotaFull : ShareResult()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object NotAvailable : SaveResult()
        data class Failed(val cause: Throwable) : SaveResult()
        data object Cancelled : SaveResult()
    }

    sealed class OpenResult {
        data class Success(val contentUri: android.net.Uri, val mimeType: String) : OpenResult()
        data object NotAvailable : OpenResult()
        data class Failed(val cause: Throwable) : OpenResult()
        data object Cancelled : OpenResult()
    }

    private sealed class DownloadResult {
        data object Success : DownloadResult()
        data object NotAvailable : DownloadResult()
        data class Failed(val cause: Throwable) : DownloadResult()
        data object Cancelled : DownloadResult()
    }

    sealed class DeleteResult {
        data object Deleted : DeleteResult()
        data object NotFound : DeleteResult()
        data class Partial(val remainingConnectors: Set<String>) : DeleteResult()
    }

    data class Transfer(
        val blobKey: String,
        val fileName: String,
        val direction: Direction,
        val progress: BlobProgress,
    ) {
        enum class Direction { UPLOAD, DOWNLOAD }
    }

    private val _transfers = MutableStateFlow<Map<String, Transfer>>(emptyMap())

    /**
     * In-flight transfer jobs keyed by blobKey. Used by [cancelTransfer] to interrupt an upload
     * or download. Kept separate from [_transfers] so the [Job] reference doesn't leak through
     * the public StateFlow.
     */
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /**
     * Progress of in-flight blob transfers keyed by blob key string. Upload entries appear while
     * [shareFile] is running; download entries while [saveFile] is running. Entries are removed
     * when the operation completes (success or failure).
     */
    val transfers: StateFlow<Map<String, Transfer>> = _transfers.asStateFlow()

    private val stagingDir: File
        get() = blobCacheDirs.staging

    private val downloadDir: File
        get() = blobCacheDirs.download

    private val openCacheDir: File
        get() = blobCacheDirs.openCache

    suspend fun shareFile(uri: android.net.Uri): ShareResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "shareFile(uri=$uri)" }
        val blobKey = BlobKey(UUID.randomUUID().toString())
        val currentJob = coroutineContext[Job]
            ?: error("shareFile must run in a coroutine with a Job")
        activeJobs[blobKey.id] = currentJob

        val stagedFile = blobCacheDirs.tempFile(stagingDir)
        val stagedPath = stagedFile.absolutePath.toPath()
        // Set after blobManager.put returns successfully and cleared after upsertFile commits.
        // If the surrounding scope is cancelled while non-null, the just-finalized server-side
        // sessions are orphans (no module references them) — explicit abortPostFinalize cleans
        // them in seconds instead of waiting on the server's idle GC.
        var pendingPostFinalizeCleanup: BlobManager.PutResult? = null
        try {
            val contentResolver = context.contentResolver
            val (displayName, mimeType) = queryFileMetadata(contentResolver, uri)

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

            val metadata = BlobMetadata(
                size = size,
                createdAt = Clock.System.now(),
                checksum = checksum,
            )

            val putResult = try {
                _transfers.update {
                    it + (blobKey.id to Transfer(
                        blobKey = blobKey.id,
                        fileName = displayName,
                        direction = Transfer.Direction.UPLOAD,
                        progress = BlobProgress(bytesTransferred = 0L, bytesTotal = size),
                    ))
                }
                blobManager.put(
                    deviceId = syncSettings.deviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = blobKey,
                    openSource = { FileSystem.SYSTEM.source(stagedPath) },
                    metadata = metadata,
                    onProgress = { progress ->
                        _transfers.update {
                            val prev = it[blobKey.id] ?: return@update it
                            it + (blobKey.id to prev.copy(progress = progress))
                        }
                    },
                )
            } finally {
                _transfers.update { it - blobKey.id }
            }

            if (putResult.successful.isEmpty()) {
                log(TAG, WARN) { "shareFile(): All connectors failed" }
                if (putResult.perConnectorErrors.isEmpty()) {
                    return@withContext ShareResult.NoEligibleConnectors
                }
                val errs = putResult.perConnectorErrors.values
                if (errs.isNotEmpty() && errs.all { it is BlobServerStorageLowException }) {
                    return@withContext ShareResult.ServerStorageLow
                }
                if (errs.isNotEmpty() && errs.all { it is BlobQuotaExceededException }) {
                    return@withContext ShareResult.AccountQuotaFull
                }
                val tooLargeErrors = errs.filterIsInstance<BlobFileTooLargeException>()
                val allTooLarge = tooLargeErrors.size == errs.size
                if (allTooLarge) {
                    val minCap = tooLargeErrors.mapNotNull { it.maxFileBytes }.minOrNull()
                    if (minCap != null) {
                        return@withContext ShareResult.FileTooLarge(requestedBytes = size, maxBytes = minCap)
                    }
                }
                return@withContext ShareResult.AllConnectorsFailed(putResult.perConnectorErrors)
            }

            // Open the cleanup window — covers the gap between put returning and upsertFile
            // taking effect. Cancellation in here triggers abortPostFinalize.
            pendingPostFinalizeCleanup = putResult

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

            // Force the just-finalized blob to land in a persisted module revision before we
            // declare the share done. BaseModuleRepo's writeFLow eventually picks up the
            // upsertFile change and syncs, but it throttles by 1 s and is fully decoupled —
            // process death between finalize and that sync would orphan the local row against
            // a server-side COMPLETE session that the server GCs after `completeIdleTtlSeconds`
            // (10 min by default). Driving the sync here, awaiting it, then closing the cleanup
            // window means: success implies the commit hit each receiving connector, which
            // makes the just-uploaded blob immutable and survives any future app death.
            //
            // If a parallel sync already holds the lock, syncManager.sync sets `pendingSync`
            // and returns early. Acceptable: that other sync was already going to write our
            // state and will re-iterate to pick up `pendingSync`. The remaining race is short
            // (the in-flight sync's network round-trip), much smaller than the prior 1 s+
            // throttle window.
            val selfData = ModuleData(
                modifiedAt = Clock.System.now(),
                deviceId = syncSettings.deviceId,
                moduleId = FileShareModule.MODULE_ID,
                data = fileShareHandler.currentOwn(),
            )
            fileShareSync.sync(selfData)
            syncManager.sync(
                SyncOptions(
                    stats = false,
                    readData = false,
                    writeData = true,
                    moduleFilter = setOf(FileShareModule.MODULE_ID),
                ),
            )
            pendingPostFinalizeCleanup = null
            // Invalidate the storage cache for connectors that received the blob — the server-side
            // numbers just changed. Done after commit (not inside BlobManager.put) so an aborted
            // commit doesn't leave us holding a stale "we just used X bytes" snapshot.
            storageStatusManager.invalidateAndRefresh(putResult.successful)

            if (putResult.perConnectorErrors.isEmpty()) {
                ShareResult.Success
            } else {
                ShareResult.PartialMirror(putResult.successful, putResult.perConnectorErrors)
            }
        } catch (e: CancellationException) {
            log(TAG, INFO) { "shareFile(${blobKey.id}) cancelled" }
            pendingPostFinalizeCleanup?.let { result ->
                withContext(NonCancellable) {
                    try {
                        blobManager.abortPostFinalize(
                            deviceId = syncSettings.deviceId,
                            moduleId = FileShareModule.MODULE_ID,
                            targets = result.remoteRefs,
                        )
                    } catch (e: Exception) {
                        log(TAG, WARN) { "abortPostFinalize cleanup failed: ${e.message}" }
                    }
                }
            }
            ShareResult.Cancelled
        } finally {
            activeJobs.remove(blobKey.id)
            stagedFile.delete()
        }
    }

    suspend fun saveFile(
        sharedFile: FileShareInfo.SharedFile,
        ownerDeviceId: DeviceId,
        destinationUri: android.net.Uri,
    ): SaveResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "saveFile(blobKey=${sharedFile.blobKey}, owner=$ownerDeviceId)" }

        val downloadFile = blobCacheDirs.tempFile(downloadDir)
        try {
            when (val r = downloadAndDecryptToCache(sharedFile, ownerDeviceId, downloadFile)) {
                DownloadResult.Success -> Unit
                DownloadResult.NotAvailable -> return@withContext SaveResult.NotAvailable
                is DownloadResult.Failed -> return@withContext SaveResult.Failed(r.cause)
                DownloadResult.Cancelled -> return@withContext SaveResult.Cancelled
            }

            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext SaveResult.Failed(IllegalStateException("Failed to open destination output stream"))

            outputStream.use { output ->
                downloadFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            SaveResult.Success
        } catch (e: CancellationException) {
            log(TAG, INFO) { "saveFile(${sharedFile.blobKey}) cancelled" }
            SaveResult.Cancelled
        } catch (e: Exception) {
            log(TAG, ERROR) { "saveFile() failed: ${e.asLog()}" }
            SaveResult.Failed(e)
        } finally {
            downloadFile.delete()
        }
    }

    /**
     * Decrypt the file referenced by [sharedFile] into the OPEN_CACHE under a stable filename
     * derived from name + checksum. If the file already exists (cache hit), only mtime is touched
     * so [BlobMaintenance] keeps it. Returns a content URI through [FileProvider].
     */
    suspend fun openFile(
        sharedFile: FileShareInfo.SharedFile,
        ownerDeviceId: DeviceId,
    ): OpenResult = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "openFile(blobKey=${sharedFile.blobKey}, owner=$ownerDeviceId)" }

        val target = openCacheFileFor(sharedFile)
        if (target.isFile && target.length() == sharedFile.size) {
            // Size match alone isn't sufficient: a tampered or truncated-then-corrupted cache
            // entry can preserve byte length while changing contents. Re-hash on every hit so
            // an open-cache compromise can't yield a payload that mismatches the synced
            // checksum. ~50 ms on a 10 MB file — acceptable overhead per open.
            val cachedHash = hashFile(target)
            if (cachedHash == sharedFile.checksum) {
                target.setLastModified(System.currentTimeMillis())
                log(TAG, VERBOSE) { "openFile(): cache hit for ${sharedFile.blobKey}" }
                return@withContext openResultFor(target, sharedFile)
            }
            log(TAG, WARN) {
                "openFile(): cache file for ${sharedFile.blobKey} failed checksum verification " +
                    "(expected=${sharedFile.checksum.take(16)}, actual=${cachedHash.take(16)}); re-downloading"
            }
            target.delete()
        }

        val tempFile = blobCacheDirs.tempFile(openCacheDir)
        try {
            when (val r = downloadAndDecryptToCache(sharedFile, ownerDeviceId, tempFile)) {
                DownloadResult.Success -> Unit
                DownloadResult.NotAvailable -> return@withContext OpenResult.NotAvailable
                is DownloadResult.Failed -> return@withContext OpenResult.Failed(r.cause)
                DownloadResult.Cancelled -> return@withContext OpenResult.Cancelled
            }

            // Atomic rename; both files are in the same directory so this should always succeed.
            target.delete()
            if (!tempFile.renameTo(target)) {
                return@withContext OpenResult.Failed(IllegalStateException("rename to open-cache failed"))
            }
            target.setLastModified(System.currentTimeMillis())
            openResultFor(target, sharedFile)
        } catch (e: CancellationException) {
            log(TAG, INFO) { "openFile(${sharedFile.blobKey}) cancelled" }
            OpenResult.Cancelled
        } catch (e: Exception) {
            log(TAG, ERROR) { "openFile() failed: ${e.asLog()}" }
            OpenResult.Failed(e)
        } finally {
            if (tempFile.exists() && tempFile.absolutePath != target.absolutePath) tempFile.delete()
        }
    }

    private fun openResultFor(file: File, sharedFile: FileShareInfo.SharedFile): OpenResult.Success {
        val authority = "${BuildConfigWrap.APPLICATION_ID}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val mimeType = sharedFile.mimeType.ifBlank { "application/octet-stream" }
        return OpenResult.Success(uri, mimeType)
    }

    /**
     * Stable open-cache filename: `<sha8>_<sanitizedName>` so the original extension survives
     * for the system viewer and content-different copies don't collide.
     */
    private fun openCacheFileFor(sharedFile: FileShareInfo.SharedFile): File {
        val sanitized = sharedFile.name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(96)
            .ifEmpty { "file" }
        val tag = sharedFile.checksum.take(8)
        return File(openCacheDir, "${tag}_$sanitized")
    }

    /** Lowercase hex SHA-256 of the entire file contents. */
    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun downloadAndDecryptToCache(
        sharedFile: FileShareInfo.SharedFile,
        ownerDeviceId: DeviceId,
        target: File,
    ): DownloadResult {
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
            log(TAG, WARN) { "downloadAndDecryptToCache(): No available connectors overlap with configured+resolvable" }
            return DownloadResult.NotAvailable
        }

        val targetPath = target.absolutePath.toPath()
        val currentJob = coroutineContext[Job]
            ?: error("downloadAndDecryptToCache must run in a coroutine with a Job")
        activeJobs[sharedFile.blobKey] = currentJob
        return try {
            _transfers.update {
                it + (sharedFile.blobKey to Transfer(
                    blobKey = sharedFile.blobKey,
                    fileName = sharedFile.name,
                    direction = Transfer.Direction.DOWNLOAD,
                    progress = BlobProgress(bytesTransferred = 0L, bytesTotal = sharedFile.size),
                ))
            }
            blobManager.get(
                deviceId = ownerDeviceId,
                moduleId = FileShareModule.MODULE_ID,
                blobKey = BlobKey(sharedFile.blobKey),
                candidates = candidates,
                expectedPlaintextSize = sharedFile.size,
                openSink = { FileSystem.SYSTEM.sink(targetPath) },
                onProgress = { progress ->
                    _transfers.update {
                        val prev = it[sharedFile.blobKey] ?: return@update it
                        it + (sharedFile.blobKey to prev.copy(progress = progress))
                    }
                },
            )

            // Verify checksum against synced metadata. SharedFile.checksum is required to be
            // non-blank by the data class init, so a checksum is always available here.
            val actualChecksum = hashFile(target)
            if (actualChecksum != sharedFile.checksum) {
                return DownloadResult.Failed(
                    BlobChecksumMismatchException(
                        blobKey = BlobKey(sharedFile.blobKey),
                        expected = sharedFile.checksum,
                        actual = actualChecksum,
                    )
                )
            }

            DownloadResult.Success
        } catch (e: CancellationException) {
            DownloadResult.Cancelled
        } catch (e: Exception) {
            log(TAG, ERROR) { "downloadAndDecryptToCache() failed: ${e.asLog()}" }
            DownloadResult.Failed(e)
        } finally {
            activeJobs.remove(sharedFile.blobKey)
            _transfers.update { it - sharedFile.blobKey }
        }
    }

    /**
     * Cancel an in-flight share or save for [blobKey], if one is running. The cancelled
     * coroutine returns [ShareResult.Cancelled] / [SaveResult.Cancelled]; staging files are
     * cleaned up by the existing finally blocks. No-op if [blobKey] has no active transfer.
     */
    fun cancelTransfer(blobKey: String) {
        val job = activeJobs[blobKey] ?: run {
            log(TAG, INFO) { "cancelTransfer($blobKey): no active transfer" }
            return
        }
        log(TAG, INFO) { "cancelTransfer($blobKey)" }
        job.cancel(CancellationException("user-cancelled"))
    }

    /**
     * Force a retry of mirror uploads for the file identified by [blobKey] across all currently
     * configured connectors that don't already have it. Clears the per-(connector, blobKey)
     * backoff state first so the maintenance loop will pick the file up immediately, then runs
     * a single targeted mirror pass.
     */
    suspend fun retryMirror(blobKey: String) = withContext(dispatcherProvider.IO) {
        log(TAG, INFO) { "retryMirror($blobKey)" }
        blobManager.clearBackoff(BlobKey(blobKey))
        blobMaintenance.runMirrorUploadsFor(setOf(blobKey))
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
        val result = if (newAvailableOn.isEmpty()) {
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
        // Invalidate after the metadata write — the freed bytes only count once the module commit
        // lands. Keys off `successfulDeletes` so failed-delete connectors don't get a useless probe.
        storageStatusManager.invalidateAndRefresh(successfulDeletes)
        result
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
