package eu.darken.octi.modules.files.core

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag

/**
 * Result of probing a content URI for the metadata the upload orchestrator needs to pick a tier.
 *
 * The probe deliberately does NOT test re-openability — opening twice can itself burn a true
 * single-shot stream. Re-openability is discovered at runtime when the streaming pipeline tries
 * to open a second time, and we drop to staging (Tier A) on failure.
 */
sealed class UriProbe {
    /** `OpenableColumns.SIZE` returned a non-negative plaintext-size. */
    data class Sized(val plaintextSize: Long) : UriProbe()

    /** No size column, or column was null/negative. */
    data object Unsized : UriProbe()
}

/**
 * Query [OpenableColumns.SIZE] for [uri] and classify the result. Never throws — a missing
 * provider, missing column, or null value all collapse to [UriProbe.Unsized].
 */
fun ContentResolver.probeUriForUpload(uri: Uri): UriProbe = try {
    query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            val size = cursor.getLong(0)
            if (size >= 0) UriProbe.Sized(size) else UriProbe.Unsized
        } else UriProbe.Unsized
    } ?: UriProbe.Unsized
} catch (e: Exception) {
    log(TAG, VERBOSE) { "probeUriForUpload($uri) failed, treating as Unsized: ${e.message}" }
    UriProbe.Unsized
}

private val TAG = logTag("Module", "Files", "UriProbe")
