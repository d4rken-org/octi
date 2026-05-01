package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
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
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.StorageStatusManager
import kotlin.time.Duration
import eu.darken.octi.sync.core.blob.BlobMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Standalone maintenance singleton that runs independently of [FileShareSettings.isEnabled].
 * Handles: retry mirror uploads, prune expired files, clean up partially deleted blobs.
 * Uses [FileShareCache] directly (not [FileShareRepo]) so disabled modules can still drain.
 */
@Singleton
class BlobMaintenance @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val blobManager: BlobManager,
    private val storageStatusManager: StorageStatusManager,
    private val fileShareHandler: FileShareHandler,
    private val fileShareCache: FileShareCache,
    private val fileShareSettings: FileShareSettings,
    private val syncSettings: SyncSettings,
    private val blobCacheDirs: BlobCacheDirs,
    private val publisher: FileSharePublisher,
) {

    private val stagingDir: File
        get() = blobCacheDirs.maintenance

    /**
     * Serialises [runOnce] and any user-triggered [retryMirrorUploads] call so a Retry tap
     * can't run a second mirror pass concurrent with the periodic 30-min one.
     */
    private val maintenanceLock = Mutex()

    fun start() {
        log(TAG, INFO) { "start(): first maintenance run in $STARTUP_DELAY" }
        scope.launch(dispatcherProvider.IO) {
            delay(STARTUP_DELAY)
            while (isActive) {
                try {
                    runOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Maintenance failed: ${e.asLog()}" }
                }
                delay(MAINTENANCE_INTERVAL)
            }
        }
    }

    internal suspend fun runOnce() = maintenanceLock.withLock {
        log(TAG) { "runOnce() starting" }
        cleanupStaleTempFiles()
        consumeRemoteDeleteRequests()
        retryMirrorUploads()
        retryPendingDeletes()
        pruneExpired()
        pruneOwnDeleteRequests()
        trimBackoff()
        log(TAG) { "runOnce() complete" }
    }

    /**
     * Public entry point for the FileShareService row-level Retry button. Runs only the mirror
     * pass, scoped to the supplied [only] set of blobKeys, under the same maintenance lock as
     * the periodic [runOnce]. Does NOT run delete / expiry / cleanup so a Retry on one row
     * cannot trigger a delete on another that just expired.
     */
    suspend fun runMirrorUploadsFor(only: Set<String>) = withContext(dispatcherProvider.IO) {
        maintenanceLock.withLock {
            log(TAG, INFO) { "runMirrorUploadsFor(only=$only)" }
            retryMirrorUploads(only = only)
        }
    }

    private suspend fun trimBackoff() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val keep = own.files.map { BlobKey(it.blobKey) }.toSet()
        blobManager.trimBackoff(keep)
    }

    /**
     * Delete leftover cache files past their per-Kind TTL. Short-lived buffers (staging,
     * download, maintenance, encryption) age out at 1h; the open-cache (decrypted plaintext for
     * external viewers) keeps for 24h so the user can re-open without redownloading.
     */
    private fun cleanupStaleTempFiles() {
        val now = System.currentTimeMillis()
        blobCacheDirs.forEachExistingKindDir { kind, dir ->
            val ttlMs = ttlFor(kind).inWholeMilliseconds
            val cutoff = now - ttlMs
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    if (file.delete()) log(TAG) { "cleanupStaleTempFiles(): deleted ${file.path}" }
                }
            }
        }
    }

    private fun ttlFor(kind: BlobCacheDirs.Kind): Duration = when (kind) {
        BlobCacheDirs.Kind.OPEN_CACHE -> OPEN_CACHE_TTL
        else -> SHORT_TTL
    }

    private suspend fun retryMirrorUploads(only: Set<String>? = null) {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val configuredById = blobManager.configuredConnectorsByIdString()
        val pendingDeletes = fileShareSettings.pendingDeletes.value()
        val touched = mutableSetOf<ConnectorId>()

        for (file in own.files) {
            if (only != null && file.blobKey !in only) continue
            if (Clock.System.now() > file.expiresAt) continue
            if (file.blobKey in pendingDeletes) continue

            val blobKey = BlobKey(file.blobKey)
            val availableCandidates: Map<ConnectorId, RemoteBlobRef> = file.availableOn
                .mapNotNull { idStr ->
                    val connectorId = configuredById[idStr] ?: return@mapNotNull null
                    val ref = file.connectorRefs[idStr] ?: return@mapNotNull null
                    connectorId to ref
                }
                .toMap()
            if (availableCandidates.isEmpty()) continue

            val missingTargets = configuredById.values
                .filter { it.idString !in file.availableOn }
                .filterNot { blobManager.isBackedOff(it, blobKey) }
                .toSet()
            if (missingTargets.isEmpty()) continue

            log(TAG) { "retryMirrorUploads(): Retrying ${file.name} for ${missingTargets.size} missing connectors" }

            val stagedFile = blobCacheDirs.tempFile(stagingDir)
            val stagedPath = stagedFile.absolutePath.toPath()
            try {
                try {
                    blobManager.get(
                        deviceId = syncSettings.deviceId,
                        moduleId = FileShareModule.MODULE_ID,
                        blobKey = blobKey,
                        candidates = availableCandidates,
                        expectedPlaintextSize = file.size,
                        openSink = { FileSystem.SYSTEM.sink(stagedPath) },
                    )

                    val digest = MessageDigest.getInstance("SHA-256")
                    stagedFile.inputStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            digest.update(buffer, 0, read)
                        }
                    }
                    val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
                    // SharedFile.checksum is required to be non-blank (init contract), so this is
                    // an unconditional integrity gate against ciphertext tampering or download
                    // corruption that the streaming AEAD didn't catch.
                    if (actualChecksum != file.checksum) {
                        log(TAG, ERROR) { "retryMirrorUploads(): Skipping ${file.name}, checksum mismatch" }
                        continue
                    }

                    val result = blobManager.put(
                        deviceId = syncSettings.deviceId,
                        moduleId = FileShareModule.MODULE_ID,
                        blobKey = blobKey,
                        openSource = { FileSystem.SYSTEM.source(stagedPath) },
                        metadata = BlobMetadata(
                            size = file.size,
                            createdAt = file.sharedAt,
                            checksum = file.checksum,
                        ),
                        eligibleConnectors = missingTargets,
                    )

                    if (result.successful.isNotEmpty()) {
                        val newAvailableOn = file.availableOn + result.successful.map { it.idString }
                        val newConnectorRefs = file.connectorRefs +
                            result.remoteRefs.mapKeys { (connectorId, _) -> connectorId.idString }
                        fileShareHandler.updateLocations(
                            blobKey = file.blobKey,
                            newAvailableOn = newAvailableOn,
                            newConnectorRefs = newConnectorRefs,
                        )
                        touched += result.successful
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "retryMirrorUploads(): Failed for ${file.name}: ${e.asLog()}" }
                }
            } finally {
                stagedFile.delete()
            }
        }
        // Single batch invalidation after the whole pass — refreshes once per connector even
        // if multiple files mirrored to the same connector this round.
        storageStatusManager.invalidateAndRefresh(touched)
    }

    private suspend fun consumeRemoteDeleteRequests() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val now = Clock.System.now()
        val requestedBlobKeys = fileShareCache.cachedDevices()
            .filter { it != syncSettings.deviceId }
            .mapNotNull { fileShareCache.get(it)?.data }
            .flatMap { it.deleteRequests }
            .filter { it.targetDeviceId == syncSettings.deviceId.id && now <= it.retainUntil }
            .map { it.blobKey }
            .toSet()
        if (requestedBlobKeys.isEmpty()) return

        val configuredById = blobManager.configuredConnectorsByIdString()
        val touched = mutableSetOf<ConnectorId>()
        for (file in own.files.filter { it.blobKey in requestedBlobKeys }) {
            try {
                touched += deleteOwnFileForRequest(file, configuredById)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "consumeRemoteDeleteRequests(): Failed for ${file.name}: ${e.asLog()}" }
            }
        }
        if (touched.isEmpty()) return
        storageStatusManager.invalidateAndRefresh(touched)
        publisher.publishNow()
    }

    private suspend fun deleteOwnFileForRequest(
        file: FileShareInfo.SharedFile,
        configuredById: Map<String, ConnectorId>,
    ): Set<ConnectorId> {
        log(TAG) { "deleteOwnFileForRequest(): ${file.name}" }
        val targets: Map<ConnectorId, RemoteBlobRef> = file.availableOn
            .mapNotNull { idStr ->
                val connectorId = configuredById[idStr] ?: return@mapNotNull null
                val ref = file.connectorRefs[idStr] ?: return@mapNotNull null
                connectorId to ref
            }
            .toMap()

        if (targets.isEmpty()) {
            val tombstone = PendingDelete(
                blobKey = file.blobKey,
                remainingConnectors = file.availableOn,
                createdAt = Clock.System.now(),
            )
            fileShareSettings.pendingDeletes.update { it + (file.blobKey to tombstone) }
            return emptySet()
        }

        val successfulDeletes = blobManager.delete(
            deviceId = syncSettings.deviceId,
            moduleId = FileShareModule.MODULE_ID,
            blobKey = BlobKey(file.blobKey),
            targets = targets,
        )

        val deletedIds = successfulDeletes.map { it.idString }.toSet()
        val newAvailableOn = file.availableOn - deletedIds
        val newConnectorRefs = file.connectorRefs - deletedIds
        if (newAvailableOn.isEmpty()) {
            fileShareHandler.removeFile(file.blobKey)
            fileShareSettings.pendingDeletes.update { it - file.blobKey }
        } else if (newAvailableOn != file.availableOn) {
            fileShareHandler.updateLocations(file.blobKey, newAvailableOn, newConnectorRefs)
            val tombstone = PendingDelete(
                blobKey = file.blobKey,
                remainingConnectors = newAvailableOn,
                createdAt = Clock.System.now(),
            )
            fileShareSettings.pendingDeletes.update { it + (file.blobKey to tombstone) }
        }
        return successfulDeletes
    }

    private suspend fun pruneExpired() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val now = Clock.System.now()
        val configuredById = blobManager.configuredConnectorsByIdString()
        val touched = mutableSetOf<ConnectorId>()

        for (file in own.files) {
            if (now <= file.expiresAt) continue

            log(TAG) { "pruneExpired(): Cleaning up expired file ${file.name}" }
            try {
                val blobKey = BlobKey(file.blobKey)
                val targets: Map<ConnectorId, RemoteBlobRef> = file.availableOn
                    .mapNotNull { idStr ->
                        val connectorId = configuredById[idStr] ?: return@mapNotNull null
                        val ref = file.connectorRefs[idStr] ?: return@mapNotNull null
                        connectorId to ref
                    }
                    .toMap()

                if (targets.isEmpty()) {
                    if (now >= file.expiresAt + STALE_FORGET_DELAY) {
                        fileShareHandler.removeFile(file.blobKey)
                        fileShareSettings.pendingDeletes.update { it - file.blobKey }
                    }
                    continue
                }

                val successfulDeletes = blobManager.delete(
                    deviceId = syncSettings.deviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = blobKey,
                    targets = targets,
                )
                touched += successfulDeletes

                val deletedIds = successfulDeletes.map { it.idString }.toSet()
                val newAvailableOn = file.availableOn - deletedIds
                val newConnectorRefs = file.connectorRefs - deletedIds
                if (newAvailableOn.isEmpty()) {
                    fileShareHandler.removeFile(file.blobKey)
                    fileShareSettings.pendingDeletes.update { it - file.blobKey }
                } else if (newAvailableOn != file.availableOn) {
                    // Patch both fields atomically — earlier code only updated availableOn,
                    // leaving stale connectorRefs that the next sync would still commit.
                    fileShareHandler.updateLocations(file.blobKey, newAvailableOn, newConnectorRefs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "pruneExpired(): Failed to clean ${file.name}: ${e.asLog()}" }
            }
        }
        storageStatusManager.invalidateAndRefresh(touched)
    }

    private suspend fun retryPendingDeletes() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val pendingDeletes = fileShareSettings.pendingDeletes.value()
        if (pendingDeletes.isEmpty()) return

        val configuredById = blobManager.configuredConnectorsByIdString()
        var remainingDeletes = pendingDeletes
        val touched = mutableSetOf<ConnectorId>()

        for (tombstone in pendingDeletes.values) {
            val blobKeyStr = tombstone.blobKey
            val file = own.files.find { it.blobKey == blobKeyStr }
            if (file == null) {
                remainingDeletes = remainingDeletes - blobKeyStr
                continue
            }

            // Restrict targets to tombstone.remainingConnectors if specified
            // (empty = unknown, try all configured connectors that match availableOn)
            val targets: Map<ConnectorId, RemoteBlobRef> = file.availableOn
                .filter { idStr ->
                    tombstone.remainingConnectors.isEmpty() || idStr in tombstone.remainingConnectors
                }
                .mapNotNull { idStr ->
                    val connectorId = configuredById[idStr] ?: return@mapNotNull null
                    val ref = file.connectorRefs[idStr] ?: return@mapNotNull null
                    connectorId to ref
                }
                .toMap()
            if (targets.isEmpty()) continue

            try {
                val successfulDeletes = blobManager.delete(
                    deviceId = syncSettings.deviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = BlobKey(blobKeyStr),
                    targets = targets,
                )

                touched += successfulDeletes
                if (successfulDeletes.isEmpty()) continue

                val deletedIds = successfulDeletes.map { it.idString }.toSet()
                val newAvailableOn = file.availableOn - deletedIds
                val newConnectorRefs = file.connectorRefs - deletedIds
                if (newAvailableOn.isEmpty()) {
                    fileShareHandler.removeFile(blobKeyStr)
                    remainingDeletes = remainingDeletes - blobKeyStr
                } else {
                    fileShareHandler.updateLocations(blobKeyStr, newAvailableOn, newConnectorRefs)
                    // Update tombstone: remaining = remaining - deletedIds (or recompute from availableOn)
                    val newRemaining = if (tombstone.remainingConnectors.isEmpty()) {
                        emptySet<String>()
                    } else {
                        tombstone.remainingConnectors - deletedIds
                    }
                    remainingDeletes = remainingDeletes + (blobKeyStr to tombstone.copy(remainingConnectors = newRemaining))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "retryPendingDeletes(): Failed for ${file.name}: ${e.asLog()}" }
            }
        }

        if (remainingDeletes != pendingDeletes) {
            fileShareSettings.pendingDeletes.value(remainingDeletes)
        }
        storageStatusManager.invalidateAndRefresh(touched)
    }

    private suspend fun pruneOwnDeleteRequests() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        if (own.deleteRequests.isEmpty()) return

        val now = Clock.System.now()
        // Pre-fetch target snapshots OUTSIDE the state-update lock — fileShareCache.get is suspend
        // and must not be called from inside the DynamicStateFlow mutation.
        val targetSnapshots: Map<String, FileShareInfo?> = own.deleteRequests
            .map { it.targetDeviceId }
            .toSet()
            .associateWith { fileShareCache.get(DeviceId(it))?.data }

        fileShareHandler.mutateDeleteRequests { requests ->
            requests.filter { request ->
                if (now > request.retainUntil) return@filter false
                val targetData = targetSnapshots[request.targetDeviceId]
                    ?: return@filter true // null = transient cache miss, keep until retainUntil
                targetData.files.any { it.blobKey == request.blobKey && now <= it.expiresAt }
            }
        }
    }

    companion object {
        private val TAG = logTag("Module", "Files", "BlobMaintenance")
        private val STARTUP_DELAY = 60.seconds
        private val MAINTENANCE_INTERVAL = 30.minutes
        /**
         * How long after expiry before a stale file is purged from the owner's own list.
         * Kept aligned with [FileShareService.DELETE_REQUEST_RETENTION] so a delete request
         * never outlives its target file in the owner's cache — the two 24-hour windows are
         * intentionally the same.
         */
        internal val STALE_FORGET_DELAY = 24.hours
        private val SHORT_TTL = 1.hours
        private val OPEN_CACHE_TTL = 24.hours
    }
}
