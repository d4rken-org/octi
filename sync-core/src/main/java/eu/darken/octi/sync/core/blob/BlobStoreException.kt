package eu.darken.octi.sync.core.blob

import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import java.io.IOException

sealed class BlobStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

class BlobFileTooLargeException(
    val connectorId: ConnectorId,
    val constraints: BlobStoreConstraints,
    val requestedBytes: Long,
) : BlobStoreException("File too large ($requestedBytes bytes) for connector $connectorId (max=${constraints.maxFileBytes})")

class BlobQuotaExceededException(
    val quota: BlobStoreQuota,
    val requestedBytes: Long,
) : BlobStoreException("Quota exceeded on ${quota.connectorId}: ${quota.usedBytes}/${quota.totalBytes} used, requested $requestedBytes")

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
