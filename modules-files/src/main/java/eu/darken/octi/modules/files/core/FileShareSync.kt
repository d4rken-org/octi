package eu.darken.octi.modules.files.core

import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.BaseModuleSync
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.modules.files.FileShareModule
import eu.darken.octi.sync.core.SyncManager
import eu.darken.octi.sync.core.SyncSettings
import eu.darken.octi.sync.core.SyncWrite
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileShareSync @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    syncSettings: SyncSettings,
    syncManager: SyncManager,
    private val fileShareSerializer: FileShareSerializer,
) : BaseModuleSync<FileShareInfo>(
    tag = TAG,
    moduleId = FileShareModule.MODULE_ID,
    dispatcherProvider = dispatcherProvider,
    syncSettings = syncSettings,
    syncManager = syncManager,
    moduleSerializer = fileShareSerializer,
) {

    public override fun serialize(item: FileShareInfo): SyncWrite.Device.Module {
        val serialized = try {
            fileShareSerializer.serialize(item)
        } catch (e: Exception) {
            throw IOException("Failed to serialize FileShareInfo", e)
        }
        val attachments = item.files.map { file ->
            SyncWrite.BlobAttachment(
                logicalKey = file.blobKey,
                connectorRefs = file.connectorRefs,
                availableOn = file.availableOn,
            )
        }
        return object : SyncWrite.Device.Module {
            override val moduleId: ModuleId = FileShareModule.MODULE_ID
            override val payload: ByteString = serialized.toByteArray().toByteString()
            override val blobs: List<SyncWrite.BlobAttachment> = attachments
            override fun toString(): String = "FileShareSync(files=${attachments.size})"
        }
    }

    companion object {
        private val TAG = logTag("Module", "Files", "Sync")
    }
}
