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
