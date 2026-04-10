package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.module.core.ModuleInfoSource
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal [ModuleInfoSource] for the files module.
 * Only manages own-device state. Does NOT inject [FileShareRepo] (avoids Hilt cycle).
 * Orchestration logic (share/save/delete) lives in [FileShareService].
 */
@Singleton
class FileShareHandler @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val settings: FileShareSettings,
    private val cache: FileShareCache,
    private val syncSettings: SyncSettings,
) : ModuleInfoSource<FileShareInfo> {

    private val currentState = DynamicStateFlow<FileShareInfo>(TAG, appScope) {
        try {
            cache.get(syncSettings.deviceId)?.data ?: FileShareInfo()
        } catch (e: Exception) {
            log(TAG) { "Failed to load cached state: ${e.message}" }
            FileShareInfo()
        }
    }

    override val info: Flow<FileShareInfo> = currentState.flow
        .setupCommonEventHandlers(TAG) { "info" }
        .replayingShare(appScope)

    suspend fun currentOwn(): FileShareInfo = currentState.value()

    suspend fun updateOwn(transform: (FileShareInfo) -> FileShareInfo) {
        currentState.updateBlocking { transform(this) }
    }

    suspend fun upsertFile(file: FileShareInfo.SharedFile) {
        log(TAG, VERBOSE) { "upsertFile(${file.name}, blobKey=${file.blobKey})" }
        updateOwn { current ->
            val updated = current.files.toMutableList()
            val idx = updated.indexOfFirst { it.blobKey == file.blobKey }
            if (idx >= 0) {
                updated[idx] = file
            } else {
                updated.add(file)
            }
            current.copy(files = updated)
        }
    }

    suspend fun removeFile(blobKey: String) {
        log(TAG, VERBOSE) { "removeFile(blobKey=$blobKey)" }
        updateOwn { current ->
            current.copy(files = current.files.filter { it.blobKey != blobKey })
        }
    }

    suspend fun patchAvailableOn(blobKey: String, newAvailableOn: Set<String>) {
        log(TAG, VERBOSE) { "patchAvailableOn(blobKey=$blobKey, availableOn=$newAvailableOn)" }
        updateOwn { current ->
            current.copy(
                files = current.files.map {
                    if (it.blobKey == blobKey) it.copy(availableOn = newAvailableOn) else it
                },
            )
        }
    }

    companion object {
        internal val TAG = logTag("Module", "Files", "Handler")
    }
}
