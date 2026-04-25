package eu.darken.octi.syncs.octiserver.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.hashing.Hash
import eu.darken.octi.common.hashing.toHash
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.blob.BlobCacheDirs
import eu.darken.octi.sync.core.blob.BlobFileTooLargeException
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobNotFoundException
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.BlobProgressCallback
import eu.darken.octi.sync.core.blob.BlobQuotaExceededException
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.BlobStoreConstraints
import eu.darken.octi.sync.core.blob.BlobStoreQuota
import eu.darken.octi.sync.core.blob.CountingSink
import eu.darken.octi.sync.core.blob.StreamingPayloadCipher
import eu.darken.octi.sync.core.encryption.EncryptionMode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Sink
import okio.Source
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * OctiServer BlobStore using resumable upload sessions and server-generated blob IDs.
 *
 * Wire semantics: the caller passes a [RemoteBlobRef] whose string value is the server-assigned
 * blob id. The store never tries to resolve a client-side logical key on its own — the creating
 * device records the ref in `SharedFile.connectorRefs` at upload time, and downstream reads pass
 * it back verbatim.
 *
 * Blob deletion issues an explicit DELETE /blobs/{id}. Servers that return 404 are treated as
 * idempotent success; servers that return 405/501 fall back to the legacy implicit-GC model
 * (blob removed when the next module commit omits it from blobRefs).
 */
class OctiServerBlobStore @AssistedInject constructor(
    private val blobCacheDirs: BlobCacheDirs,
    @Assisted private val credentials: OctiServer.Credentials,
    @Assisted private val endpoint: OctiServerEndpoint,
) : BlobStore {

    init {
        require(EncryptionMode.fromTypeString(credentials.encryptionKeyset.type) == EncryptionMode.AES256_GCM_SIV) {
            "Only AES256_GCM_SIV keysets are supported for blob storage (was: ${credentials.encryptionKeyset.type})"
        }
    }

    private val cipher by lazy { StreamingPayloadCipher(credentials.encryptionKeyset) }

    /**
     * Tracks `serverBlobId → (sessionId, moduleId, finalizedAt)` for sessions that finalized
     * during the lifetime of this connector instance. Used by [abortPostFinalize] to clean up
     * the small window where the caller cancels between [put] returning and the containing
     * module write committing — without this, the orphan blob waits for the server's idle GC.
     * Entries are pruned on each access older than [RECENT_SESSION_TTL]; on process death the
     * entries are lost and the server's idle GC takes over.
     */
    private val recentSessions = ConcurrentHashMap<String, RecentSession>()

    private data class RecentSession(
        val sessionId: String,
        val moduleId: ModuleId,
        val finalizedAt: Instant,
    )

    override val connectorId: ConnectorId = ConnectorId(
        type = ConnectorType.OCTISERVER,
        subtype = credentials.serverAdress.domain,
        account = credentials.accountId.id,
    )

    private fun buildAad(deviceId: DeviceId, moduleId: ModuleId, blobKey: BlobKey): ByteArray =
        "${deviceId.id}:${moduleId.id}:${blobKey.id}".toByteArray()

    override suspend fun put(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
        source: Source,
        metadata: BlobMetadata,
        onProgress: BlobProgressCallback?,
    ): RemoteBlobRef {
        log(TAG, VERBOSE) { "put(key=${key.id}, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }

        // 1. Pre-encrypt to staging file so we know ciphertext size + hash
        val cipherFile = blobCacheDirs.tempFile(blobCacheDirs.encryption)
        var sessionId: String? = null

        try {
            val aad = buildAad(deviceId, moduleId, key)
            FileSystem.SYSTEM.sink(cipherFile.toOkioPath()).use { cipherSink ->
                source.use { cipher.encrypt(it, cipherSink, aad) }
            }

            // 2. Compute ciphertext size + SHA-256
            val cipherSize = cipherFile.length()
            val cipherHash = cipherFile.toHash(Hash.Algo.SHA256)
            val plaintextSize = metadata.size
            log(TAG, VERBOSE) { "put(${key.id}): Encrypted ${plaintextSize}B → ${cipherSize}B ciphertext" }

            // 3. Create session with CIPHERTEXT metadata
            val session = endpoint.createBlobSession(
                deviceId = deviceId,
                moduleId = moduleId,
                sizeBytes = cipherSize,
                checksum = cipherHash,
            )
            sessionId = session.sessionId
            log(TAG, INFO) { "put(${key.id}): Session created: blobId=${session.blobId}, sessionId=${session.sessionId}" }

            // 4. Chunked upload (≤1 MB per PATCH)
            var offset = session.offsetBytes
            cipherFile.inputStream().use { input ->
                if (offset > 0L) input.skip(offset)
                while (offset < cipherSize) {
                    val chunkSize = min(MAX_CHUNK_BYTES, cipherSize - offset).toInt()
                    val chunk = ByteArray(chunkSize)
                    var read = 0
                    while (read < chunkSize) {
                        val n = input.read(chunk, read, chunkSize - read)
                        if (n < 0) break
                        read += n
                    }
                    val body = chunk.sliceArray(0 until read).toRequestBody("application/octet-stream".toMediaType())
                    offset = endpoint.appendBlobSession(moduleId, session.sessionId, offset, body)
                    // Scale ciphertext progress to plaintext-equivalent so [BlobProgress] stays
                    // plaintext-uniform across backends. Cap at plaintextSize for the final chunk
                    // since ciphertext > plaintext (Tink AEAD framing overhead).
                    val plaintextDone = if (cipherSize > 0L) {
                        ((plaintextSize.toDouble() * offset) / cipherSize).toLong().coerceAtMost(plaintextSize)
                    } else {
                        plaintextSize
                    }
                    onProgress?.invoke(BlobProgress(bytesTransferred = plaintextDone, bytesTotal = plaintextSize))
                }
            }

            // 5. Finalize with ciphertext hash
            endpoint.finalizeBlobSession(moduleId, session.sessionId, cipherHash)

            log(TAG, VERBOSE) {
                "put(${key.id}): Finalized ciphertext=${cipherSize}B, hashPrefix=${cipherHash.take(16)}"
            }
            log(TAG, INFO) { "put(${key.id}): Finalized, serverBlobId=${session.blobId}" }
            recordRecentSession(session.blobId, session.sessionId, moduleId)
            return RemoteBlobRef(session.blobId)
        } catch (e: OctiServerHttpException) {
            abortSessionSafely(moduleId, sessionId)
            when (e.httpCode) {
                413 -> throw BlobFileTooLargeException(
                    connectorId = connectorId,
                    constraints = getConstraints(),
                    requestedBytes = metadata.size,
                )
                507 -> {
                    val quota = getQuota() ?: throw e
                    throw BlobQuotaExceededException(quota = quota, requestedBytes = metadata.size)
                }
                else -> throw e
            }
        } catch (e: Exception) {
            abortSessionSafely(moduleId, sessionId)
            throw e
        } finally {
            cipherFile.delete()
        }
    }

    private suspend fun abortSessionSafely(moduleId: ModuleId, sessionId: String?) {
        sessionId ?: return
        // NonCancellable so a user-cancelled upload still issues the session abort — otherwise
        // the cancelled scope would skip the abort and leave the session lingering until idle GC.
        withContext(NonCancellable) {
            try {
                endpoint.abortBlobSession(moduleId, sessionId)
            } catch (e: Exception) {
                log(TAG, WARN) { "abortSessionSafely(): Failed to abort session $sessionId: ${e.message}" }
            }
        }
    }

    private fun recordRecentSession(blobId: String, sessionId: String, moduleId: ModuleId) {
        val now = Clock.System.now()
        recentSessions[blobId] = RecentSession(sessionId, moduleId, now)
        // Lazy eviction — keeps memory bounded without a dedicated GC.
        recentSessions.entries.removeIf { now - it.value.finalizedAt > RECENT_SESSION_TTL }
    }

    override suspend fun abortPostFinalize(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) {
        val entry = recentSessions.remove(remoteRef.value) ?: return
        log(TAG, INFO) { "abortPostFinalize(${remoteRef.value}): aborting session ${entry.sessionId}" }
        abortSessionSafely(entry.moduleId, entry.sessionId)
    }

    override suspend fun get(
        deviceId: DeviceId,
        moduleId: ModuleId,
        key: BlobKey,
        remoteRef: RemoteBlobRef,
        sink: Sink,
        expectedPlaintextSize: Long,
        onProgress: BlobProgressCallback?,
    ): BlobMetadata {
        log(TAG, VERBOSE) { "get(ref=${remoteRef.value}, key=${key.id}, device=${deviceId.logLabel}, module=${moduleId.logLabel})" }

        val blobList = endpoint.listBlobs(deviceId, moduleId)
        val entry = blobList.blobs.find { it.blobId == remoteRef.value }
        val ciphertextTotal = entry?.sizeBytes ?: 0L

        val responseBody = endpoint.getBlob(deviceId, moduleId, remoteRef.value)
            ?: throw BlobNotFoundException(remoteRef.value)

        val progressingSink = if (onProgress != null) {
            CountingSink(sink) { written ->
                onProgress(BlobProgress(bytesTransferred = written, bytesTotal = expectedPlaintextSize))
            }
        } else {
            sink
        }

        responseBody.use { body ->
            cipher.decrypt(body.source(), progressingSink, buildAad(deviceId, moduleId, key))
        }

        return BlobMetadata(
            size = ciphertextTotal,
            createdAt = Clock.System.now(),
            checksum = entry?.hashHex ?: "",
        )
    }

    override suspend fun getMetadata(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef): BlobMetadata? {
        val blobList = endpoint.listBlobs(deviceId, moduleId)
        val entry = blobList.blobs.find { it.blobId == remoteRef.value } ?: return null
        return BlobMetadata(
            size = entry.sizeBytes,
            createdAt = Clock.System.now(),
            checksum = entry.hashHex ?: "",
        )
    }

    override suspend fun delete(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) {
        log(TAG, VERBOSE) { "delete(ref=${remoteRef.value})" }
        endpoint.deleteBlob(deviceId, moduleId, remoteRef.value)
    }

    override suspend fun list(deviceId: DeviceId, moduleId: ModuleId): Set<RemoteBlobRef> {
        val blobList = endpoint.listBlobs(deviceId, moduleId)
        return blobList.blobs.map { RemoteBlobRef(it.blobId) }.toSet()
    }

    override suspend fun getConstraints(): BlobStoreConstraints {
        return try {
            val storage = endpoint.getAccountStorage()
            // Reserve CIPHERTEXT_OVERHEAD_BUFFER so Tink AEAD framing (header + per-segment tags)
            // doesn't push an otherwise-valid plaintext past the server's hard limit.
            BlobStoreConstraints(
                maxFileBytes = (storage.maxBlobBytes - CIPHERTEXT_OVERHEAD_BUFFER).coerceAtLeast(0),
                maxTotalBytes = storage.accountQuotaBytes,
            )
        } catch (e: Exception) {
            log(TAG, WARN) { "getConstraints() failed, using defaults: ${e.message}" }
            BlobStoreConstraints(
                maxFileBytes = DEFAULT_MAX_BLOB_BYTES - CIPHERTEXT_OVERHEAD_BUFFER,
                maxTotalBytes = DEFAULT_QUOTA_BYTES,
            )
        }
    }

    override suspend fun getQuota(): BlobStoreQuota? {
        return try {
            val storage = endpoint.getAccountStorage()
            log(TAG, VERBOSE) {
                "getQuota(): usedBytes=${storage.usedBytes}, quota=${storage.accountQuotaBytes}, " +
                    "available=${storage.availableBytes}, reserved=${storage.reservedBytes}"
            }
            BlobStoreQuota(
                connectorId = connectorId,
                usedBytes = storage.usedBytes,
                totalBytes = storage.accountQuotaBytes,
            )
        } catch (e: Exception) {
            log(TAG, WARN) { "getQuota() failed: ${e.message}" }
            null
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(credentials: OctiServer.Credentials, endpoint: OctiServerEndpoint): OctiServerBlobStore
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "BlobStore")
        private const val DEFAULT_MAX_BLOB_BYTES = 10L * 1024 * 1024
        private const val DEFAULT_QUOTA_BYTES = 50L * 1024 * 1024
        private const val MAX_CHUNK_BYTES = 1L * 1024 * 1024

        // Conservative buffer for Tink AesGcmHkdfStreaming overhead (header + per-segment tags).
        // Observed ~192 B for 10 MB with 1 MB segments; 1 KB is plenty of headroom.
        private const val CIPHERTEXT_OVERHEAD_BUFFER = 1024L

        // How long the recent-session map remembers a finalized session for cancel cleanup. A
        // little above the server's COMPLETE_IDLE_TTL_SECONDS so we always have a useful handle
        // before the server reaps it on its own.
        private val RECENT_SESSION_TTL = 15.minutes
    }
}
