package eu.darken.octi.sync.core.blob

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Registry of blob-related cache subdirectories (under [Context.getCacheDir]) and
 * temp-file conventions. When adding a new blob tmp dir, register it here so
 * [eu.darken.octi.modules.files.core.BlobMaintenance] can purge stale files left
 * over from crashed runs.
 */
object BlobCacheDirs {
    /** Pre-upload staging buffer (FileShareService.shareFile). */
    const val STAGING = "blob-staging"

    /** Download buffer for saveFile / retry paths (FileShareService.saveFile). */
    const val DOWNLOAD = "blob-download"

    /** Retry-mirror staging buffer (BlobMaintenance.retryMirrorUploads). */
    const val MAINTENANCE = "blob-maintenance"

    /** Pre-encrypt ciphertext buffer (OctiServerBlobStore.put). */
    const val ENCRYPTION = "blob-enc"

    val ALL: List<String> = listOf(STAGING, DOWNLOAD, MAINTENANCE, ENCRYPTION)

    fun dir(context: Context, name: String): File =
        File(context.cacheDir, name).also { it.mkdirs() }

    fun tempFile(dir: File): File = File(dir, "${UUID.randomUUID()}.tmp")
}
