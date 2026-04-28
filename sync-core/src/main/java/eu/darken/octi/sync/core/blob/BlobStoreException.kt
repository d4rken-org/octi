package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import java.io.IOException

sealed class BlobStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

class BlobFileTooLargeException(
    val connectorId: ConnectorId,
    val maxFileBytes: Long?,
    val requestedBytes: Long,
) : BlobStoreException("File too large ($requestedBytes bytes) for connector $connectorId (max=$maxFileBytes)")

class BlobQuotaExceededException(
    val connectorId: ConnectorId,
    val usedBytes: Long,
    val totalBytes: Long,
    val accountLabel: String?,
    val requestedBytes: Long,
) : BlobStoreException("Quota exceeded on $connectorId: $usedBytes/$totalBytes used, requested $requestedBytes")

class BlobServerStorageLowException(
    val connectorId: ConnectorId,
) : BlobStoreException("Server storage low on $connectorId — operator must free disk space")

/**
 * Thrown when a blob cannot be located. [identifier] is typically a [BlobKey.id] or a
 * [eu.darken.octi.sync.core.RemoteBlobRef] value — callers pass whichever identifier
 * they used to look up the blob.
 */
class BlobNotFoundException(
    val identifier: String,
) : BlobStoreException("Blob not found: $identifier") {
    constructor(key: BlobKey) : this(key.id)
}

class BlobChecksumMismatchException(
    val blobKey: BlobKey,
    val expected: String,
    val actual: String,
) : BlobStoreException("Checksum mismatch for ${blobKey.id}: expected=$expected, actual=$actual")

/**
 * Server reported that the upload's actual byte count diverged from the size declared at
 * session-create time — either a PATCH would have exceeded the declared size, or finalize
 * found the upload short. Surface this as a typed exception so the upload orchestrator can
 * fall back to a "pre-count + retry" strategy when the source URI is re-readable, instead of
 * treating it as a generic transport failure.
 */
class BlobSizeMismatchException(
    val connectorId: ConnectorId,
    message: String,
    cause: Throwable? = null,
) : BlobStoreException(message, cause)
