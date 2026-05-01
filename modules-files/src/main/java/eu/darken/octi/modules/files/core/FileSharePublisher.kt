package eu.darken.octi.modules.files.core

import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleData
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncOptions
import eu.darken.octi.sync.core.SyncSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Shared helper that pushes the local file-share state to all configured sync connectors
 * immediately, bypassing the normal 1-second throttle in [FileShareHandler.info].
 * Used by both [FileShareService] (after inserting a delete request) and [BlobMaintenance]
 * (after consuming remote delete requests) so the requester's state is visible to peers
 * in the same sync cycle rather than waiting for the next maintenance pass.
 */
@Singleton
class FileSharePublisher @Inject constructor(
    private val syncSettings: SyncSettings,
    private val fileShareHandler: FileShareHandler,
    private val fileShareSync: FileShareSync,
    private val syncManager: SyncManager,
) {

    suspend fun publishNow() {
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
    }

    companion object {
        private val TAG = logTag("Module", "Files", "Publisher")
    }
}
