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
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
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
    private val fileShareHandler: FileShareHandler,
    private val fileShareCache: FileShareCache,
    private val fileShareSettings: FileShareSettings,
    private val syncSettings: SyncSettings,
    private val blobCacheDirs: BlobCacheDirs,
) {

    private val stagingDir: File
        get() = blobCacheDirs.maintenance

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

    internal suspend fun runOnce() {
        log(TAG) { "runOnce() starting" }
        cleanupStaleTempFiles()
        retryMirrorUploads()
        retryPendingDeletes()
        pruneExpired()
        log(TAG) { "runOnce() complete" }
    }

    /**
     * Delete leftover temp files older than 1 hour. Stale files are typically from a previous
     * process that crashed mid-operation. Skipping recent files avoids racing with concurrent
     * share/save/maintenance operations.
     */
    private fun cleanupStaleTempFiles() {
        val staleCutoffMs = System.currentTimeMillis() - 1.hours.inWholeMilliseconds
        blobCacheDirs.forEachExistingDir { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < staleCutoffMs) {
                    if (file.delete()) log(TAG) { "cleanupStaleTempFiles(): deleted ${file.path}" }
                }
            }
        }
    }

    private suspend fun retryMirrorUploads() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val configuredById = blobManager.configuredConnectorsByIdString()
        val pendingDeletes = fileShareSettings.pendingDeletes.value()

        for (file in own.files) {
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
                    if (file.checksum.isNotEmpty() && actualChecksum != file.checksum) {
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
    }

    private suspend fun pruneExpired() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val now = Clock.System.now()
        val configuredById = blobManager.configuredConnectorsByIdString()

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
    }

    private suspend fun retryPendingDeletes() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val pendingDeletes = fileShareSettings.pendingDeletes.value()
        if (pendingDeletes.isEmpty()) return

        val configuredById = blobManager.configuredConnectorsByIdString()
        var remainingDeletes = pendingDeletes

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
    }

    companion object {
        private val TAG = logTag("Module", "Files", "BlobMaintenance")
        private val STARTUP_DELAY = 60.seconds
        private val MAINTENANCE_INTERVAL = 30.minutes
        private val STALE_FORGET_DELAY = 24.hours
    }
}
