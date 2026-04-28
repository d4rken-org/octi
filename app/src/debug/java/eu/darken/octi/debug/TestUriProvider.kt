package eu.darken.octi.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Debug-only fixture provider used to exercise the Tier B′ orchestrator paths that no real Android
 * stock provider can trigger (Photos/Downloads/SAF/MediaStore all set OpenableColumns.SIZE).
 *
 * Authority: `${applicationId}.debug.testfixture`
 *
 * - `/unsized`     — query reports SIZE=null. openFile returns the 8 MiB blob.
 * - `/lying-size`  — query reports SIZE=1024 (lie). openFile returns the 8 MiB blob.
 */
class TestUriProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val variant = variantOrNull(uri) ?: return null
        val cols = projection ?: DEFAULT_COLS
        val cursor = MatrixCursor(cols)
        val row = arrayOfNulls<Any>(cols.size)
        cols.forEachIndexed { idx, col ->
            row[idx] = when (col) {
                OpenableColumns.SIZE -> when (variant) {
                    Variant.UNSIZED -> null
                    Variant.LYING_SIZE -> LIED_SIZE
                }
                OpenableColumns.DISPLAY_NAME -> variant.displayName
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        if (variantOrNull(uri) == null) return null
        return "application/octet-stream"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (variantOrNull(uri) == null) return null
        val file = ensureBlob()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<out String>?) = 0
    override fun delete(uri: Uri, sel: String?, args: Array<out String>?) = 0

    /**
     * Single-flight blob materialization. Concurrent opens block on [generateLock] and read the
     * already-generated file. Atomic write-rename keeps the final path empty until fsync lands.
     */
    private fun ensureBlob(): File {
        val ctx = context ?: error("Provider context unavailable")
        val dir = File(ctx.cacheDir, "test-fixture").apply { mkdirs() }
        val finalFile = File(dir, FINAL_NAME)
        if (finalFile.isFile && finalFile.length() == BLOB_SIZE) return finalFile

        generateLock.withLock {
            if (finalFile.isFile && finalFile.length() == BLOB_SIZE) return finalFile
            val tmp = File(dir, TMP_NAME)
            tmp.delete()
            val random = SecureRandom()
            val buf = ByteArray(64 * 1024)
            tmp.outputStream().use { out ->
                var remaining = BLOB_SIZE
                while (remaining > 0) {
                    val take = minOf(buf.size.toLong(), remaining).toInt()
                    random.nextBytes(buf)
                    out.write(buf, 0, take)
                    remaining -= take
                }
                out.fd.sync()
            }
            if (!tmp.renameTo(finalFile)) {
                tmp.delete()
                error("rename ${tmp.name} -> ${finalFile.name} failed")
            }
            log(TAG, VERBOSE) { "Generated test fixture blob: ${finalFile.absolutePath} (${finalFile.length()} bytes)" }
        }
        return finalFile
    }

    private fun variantOrNull(uri: Uri): Variant? = when (uri.lastPathSegment) {
        Variant.UNSIZED.path -> Variant.UNSIZED
        Variant.LYING_SIZE.path -> Variant.LYING_SIZE
        else -> null
    }

    private enum class Variant(val path: String, val displayName: String) {
        UNSIZED("unsized", "unsized.bin"),
        LYING_SIZE("lying-size", "lying-size.bin"),
    }

    companion object {
        private val TAG = logTag("Debug", "TestUriProvider")
        private const val BLOB_SIZE: Long = 8L * 1024 * 1024
        private const val LIED_SIZE: Long = 1024
        private const val FINAL_NAME = "test-fixture.bin"
        private const val TMP_NAME = "test-fixture.bin.tmp"
        private val DEFAULT_COLS = arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME)
        private val generateLock = ReentrantLock()

        const val AUTHORITY_SUFFIX = ".debug.testfixture"

        /** `content://<applicationId>.debug.testfixture/unsized` */
        fun unsizedUri(): Uri = Uri.parse("content://${BuildConfigWrap.APPLICATION_ID}$AUTHORITY_SUFFIX/unsized")

        /** `content://<applicationId>.debug.testfixture/lying-size` */
        fun lyingSizeUri(): Uri = Uri.parse("content://${BuildConfigWrap.APPLICATION_ID}$AUTHORITY_SUFFIX/lying-size")
    }
}
