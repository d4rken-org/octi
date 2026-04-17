package eu.darken.octi.sync.core.blob

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of blob-related cache subdirectories (under [Context.getCacheDir]) and
 * temp-file conventions. When adding a new blob tmp dir, add it to [Kind] so
 * [eu.darken.octi.modules.files.core.BlobMaintenance] can purge stale files left
 * over from crashed runs.
 */
@Singleton
class BlobCacheDirs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Pre-upload staging buffer (FileShareService.shareFile). */
    val staging: File get() = subdir(Kind.STAGING)

    /** Download buffer for saveFile / retry paths (FileShareService.saveFile). */
    val download: File get() = subdir(Kind.DOWNLOAD)

    /** Retry-mirror staging buffer (BlobMaintenance.retryMirrorUploads). */
    val maintenance: File get() = subdir(Kind.MAINTENANCE)

    /** Pre-encrypt ciphertext buffer (OctiServerBlobStore.put). */
    val encryption: File get() = subdir(Kind.ENCRYPTION)

    fun tempFile(dir: File): File = File(dir, "${UUID.randomUUID()}.tmp")

    /** Iterate existing blob cache subdirs (skips missing ones) — for maintenance scans. */
    fun forEachExistingDir(block: (File) -> Unit) {
        Kind.entries.forEach { kind ->
            val dir = File(context.cacheDir, kind.dirName)
            if (dir.isDirectory) block(dir)
        }
    }

    private fun subdir(kind: Kind): File = File(context.cacheDir, kind.dirName).also {
        check(it.isDirectory || it.mkdirs()) { "Cannot create blob cache dir: $it" }
    }

    private enum class Kind(val dirName: String) {
        STAGING("blob-staging"),
        DOWNLOAD("blob-download"),
        MAINTENANCE("blob-maintenance"),
        ENCRYPTION("blob-enc"),
    }
}
