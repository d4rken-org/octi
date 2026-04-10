package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.blob.BlobManager
import eu.darken.octi.sync.core.blob.BlobMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import okio.Path.Companion.toPath
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
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
    private val syncSettings: SyncSettings,
) {

    init {
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
        log(TAG, INFO) { "BlobMaintenance initialized, first run in $STARTUP_DELAY" }
    }

    internal suspend fun runOnce() {
        log(TAG) { "runOnce() starting" }
        retryMirrorUploads()
        pruneExpired()
        log(TAG) { "runOnce() complete" }
    }

    private suspend fun retryMirrorUploads() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val configuredIds = blobManager.configuredConnectorIds()

        for (file in own.files) {
            if (Clock.System.now() > file.expiresAt) continue

            val availableIds = file.availableOn
            val missingIds = configuredIds.filter { it.idString !in availableIds }
            if (missingIds.isEmpty()) continue

            val blobKey = BlobKey(file.blobKey)
            if (missingIds.all { blobManager.isBackedOff(it, blobKey) }) continue

            log(TAG) { "retryMirrorUploads(): Retrying ${file.name} for ${missingIds.size} missing connectors" }
            // We don't have the original file anymore for re-upload.
            // Mirror retry requires the blob to be available on at least one existing connector.
            // BlobManager.get downloads to temp, then BlobManager.put uploads from temp.
            // For v1: skip mirror retry if we don't have a local staged copy.
            // TODO: implement mirror retry via download-from-existing + upload-to-missing
        }
    }

    private suspend fun pruneExpired() {
        val own = fileShareCache.get(syncSettings.deviceId)?.data ?: return
        val now = Clock.System.now()

        for (file in own.files) {
            if (now <= file.expiresAt) continue

            log(TAG) { "pruneExpired(): Cleaning up expired file ${file.name}" }
            try {
                val blobKey = BlobKey(file.blobKey)
                val candidates = file.availableOn
                    .mapNotNull { idStr -> blobManager.configuredConnectorIds().find { it.idString == idStr } }
                    .toSet()

                val successfulDeletes = blobManager.delete(
                    deviceId = syncSettings.deviceId,
                    moduleId = FileShareModule.MODULE_ID,
                    blobKey = blobKey,
                    candidates = candidates,
                )

                val newAvailableOn = file.availableOn - successfulDeletes.map { it.idString }.toSet()
                if (newAvailableOn.isEmpty()) {
                    fileShareHandler.removeFile(file.blobKey)
                } else {
                    fileShareHandler.patchAvailableOn(file.blobKey, newAvailableOn)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "pruneExpired(): Failed to clean ${file.name}: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Module", "Files", "BlobMaintenance")
        private val STARTUP_DELAY = 60.seconds
        private val MAINTENANCE_INTERVAL = 30.minutes
    }
}
