package eu.darken.octi.syncs.octiserver.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import eu.darken.octi.sync.core.blob.BlobFileTooLargeException
import eu.darken.octi.sync.core.blob.BlobMetadata
import eu.darken.octi.sync.core.blob.BlobNotFoundException
import eu.darken.octi.sync.core.blob.BlobProgress
import eu.darken.octi.sync.core.blob.BlobProgressCallback
import eu.darken.octi.sync.core.blob.BlobQuotaExceededException
import eu.darken.octi.sync.core.blob.BlobServerStorageLowException
import eu.darken.octi.sync.core.blob.BlobSizeMismatchException
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.CountingSink
import eu.darken.octi.sync.core.blob.StorageStatusProvider
import eu.darken.octi.sync.core.blob.StreamingPayloadCipher
import eu.darken.octi.sync.core.encryption.EncryptionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Sink
import okio.Source
import okio.Timeout
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
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
    @Assisted private val credentials: OctiServer.Credentials,
    @Assisted private val endpoint: OctiServerEndpoint,
    @Assisted override val storageStatus: StorageStatusProvider,
) : BlobStore {

    init {
        require(EncryptionMode.fromTypeString(credentials.encryptionKeyset.type) == EncryptionMode.AES256_GCM_SIV) {
            "Only AES256_GCM_SIV keysets are supported for blob storage (was: ${credentials.encryptionKeyset.type})"
        }
    }

    private val cipher by lazy { StreamingPayloadCipher(credentials.encryptionKeyset) }

    /**
     * Tracks `serverBlobId → (deviceId, sessionId, moduleId, finalizedAt)` for sessions that finalized
     * during the lifetime of this connector instance. Used by [abortPostFinalize] to clean up
     * the small window where the caller cancels between [put] returning and the containing
     * module write committing — without this, the orphan blob waits for the server's idle GC.
     * Entries are pruned on each access older than [RECENT_SESSION_TTL]; on process death the
     * entries are lost and the server's idle GC takes over.
     */
    private val recentSessions = ConcurrentHashMap<String, RecentSession>()

    private data class RecentSession(
        val deviceId: DeviceId,
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

        val plaintextSize = metadata.size
        val cipherSize = cipher.ciphertextSize(plaintextSize)
        val aad = buildAad(deviceId, moduleId, key)
        log(TAG, VERBOSE) { "put(${key.id}): Predicted ${plaintextSize}B → ${cipherSize}B ciphertext" }

        var sessionId: String? = null
        try {
            // Create the session up front using the predicted ciphertext size. Checksum is
            // deferred to finalize since we only learn it as bytes flow through the pipeline.
            val session = endpoint.createBlobSession(
                deviceId = deviceId,
                moduleId = moduleId,
                sizeBytes = cipherSize,
                checksum = null,
            )
            sessionId = session.sessionId
            log(TAG, INFO) { "put(${key.id}): Session created: blobId=${session.blobId}, sessionId=${session.sessionId}" }

            val cipherHash = streamEncryptAndUpload(
                deviceId = deviceId,
                moduleId = moduleId,
                session = session,
                source = source,
                aad = aad,
                plaintextSize = plaintextSize,
                cipherSize = cipherSize,
                onProgress = onProgress,
            )

            endpoint.finalizeBlobSession(deviceId, moduleId, session.sessionId, cipherHash)

            log(TAG, VERBOSE) {
                "put(${key.id}): Finalized ciphertext=${cipherSize}B, hashPrefix=${cipherHash.take(16)}"
            }
            log(TAG, INFO) { "put(${key.id}): Finalized, serverBlobId=${session.blobId}" }
            recordRecentSession(session.blobId, deviceId, session.sessionId, moduleId)
            return RemoteBlobRef(session.blobId)
        } catch (e: OctiServerHttpException) {
            abortSessionSafely(deviceId, moduleId, sessionId)
            if (e.httpCode == 409 && isSizeMismatchBody(e.errorBody)) {
                // PATCH overflow ("Upload would exceed declared size") or finalize underflow
                // ("Upload not complete: $current/$expected") — both signal that the size we
                // declared at session create disagrees with the actual ciphertext stream. The
                // orchestrator turns this into a pre-count + retry when the source URI is
                // re-readable; otherwise it surfaces as a hard share failure.
                throw BlobSizeMismatchException(
                    connectorId = connectorId,
                    message = "Server reported size mismatch on session $sessionId: ${e.errorBody}",
                    cause = e,
                )
            }
            when (e.httpCode) {
                413 -> {
                    // Refresh the snapshot so the exception carries an up-to-date max-file cap;
                    // a refresh failure must not mask the original 413.
                    refreshStatusBestEffort()
                    val maxFile = storageStatus.status.value.lastKnown?.maxFileBytes
                    throw BlobFileTooLargeException(
                        connectorId = connectorId,
                        maxFileBytes = maxFile,
                        requestedBytes = metadata.size,
                    )
                }
                507 -> when (e.octiReason) {
                    OctiServerHttpException.REASON_SERVER_DISK_LOW ->
                        throw BlobServerStorageLowException(connectorId = connectorId)
                    OctiServerHttpException.REASON_ACCOUNT_QUOTA_EXCEEDED -> {
                        // Refresh so the surfaced exception reflects the server's current view;
                        // synthesize zeroes if the probe also fails so BlobManager.put still
                        // routes through the QuotaExceeded catch.
                        refreshStatusBestEffort(forceFresh = true)
                        val snap = storageStatus.status.value.lastKnown
                        throw BlobQuotaExceededException(
                            connectorId = connectorId,
                            usedBytes = snap?.usedBytes ?: 0L,
                            totalBytes = snap?.totalBytes ?: 0L,
                            accountLabel = snap?.accountLabel ?: credentials.accountId.id.take(8),
                            requestedBytes = metadata.size,
                        )
                    }
                    null -> {
                        // Older server without reason header — preserve previous behavior.
                        refreshStatusBestEffort(forceFresh = true)
                        val snap = storageStatus.status.value.lastKnown ?: throw e
                        throw BlobQuotaExceededException(
                            connectorId = connectorId,
                            usedBytes = snap.usedBytes,
                            totalBytes = snap.totalBytes,
                            accountLabel = snap.accountLabel,
                            requestedBytes = metadata.size,
                        )
                    }
                    else -> throw e
                }
                else -> throw e
            }
        } catch (e: Exception) {
            abortSessionSafely(deviceId, moduleId, sessionId)
            throw e
        }
    }

    /**
     * Streams [source] through Tink's AEAD encrypt into a [Channel] of ≤[MAX_CHUNK_BYTES] byte
     * arrays, draining the channel concurrently into PATCH calls. Returns the lowercase-hex
     * SHA-256 of the full ciphertext stream so the caller can finalize the session.
     *
     * Pipeline shape (Codex r2 review):
     *  - producer (Dispatchers.IO + runInterruptible): wraps Tink's encryptingOutputStream over
     *    a chunking Sink that emits 1 MB chunks via `channel.trySendBlocking(...).getOrThrow()`.
     *  - consumer (this coroutine): drains the channel, PATCHes each chunk, advances offset,
     *    fires the progress callback.
     *  - on producer error: producer calls `channel.close(cause)` → consumer's for-loop
     *    rethrows.
     *  - on consumer error: `producer.cancelAndJoin()` so the source is closed and Tink stops
     *    encrypting, then the consumer rethrows.
     *  - structured-concurrency wrapper `coroutineScope` ensures we don't leak the producer.
     */
    private suspend fun streamEncryptAndUpload(
        deviceId: DeviceId,
        moduleId: ModuleId,
        session: OctiServerApi.CreateSessionResponse,
        source: Source,
        aad: ByteArray,
        plaintextSize: Long,
        cipherSize: Long,
        onProgress: BlobProgressCallback?,
    ): String = coroutineScope {
        val ciphertextDigest = MessageDigest.getInstance("SHA-256")
        // Capacity 2 — the consumer typically processes a chunk while the producer fills the
        // next; capacity 1 would serialize them, capacity > 2 doesn't help since each chunk is
        // already 1 MB and we want bounded backpressure on memory.
        val chunks = Channel<ByteArray>(capacity = 2)

        val producer = launch(Dispatchers.IO) {
            try {
                runInterruptible {
                    encryptIntoChunks(source, aad, chunks, ciphertextDigest)
                }
                chunks.close()
            } catch (t: Throwable) {
                chunks.close(t)
                throw t
            } finally {
                // Best-effort: if Tink's internal `.use {}` didn't reach close (e.g. cancellation
                // mid-encrypt), make sure the underlying URI/file InputStream is released.
                try {
                    source.close()
                } catch (e: Exception) {
                    log(TAG, VERBOSE) { "producer source.close() ignored: ${e.message}" }
                }
            }
        }

        var offset = session.offsetBytes
        try {
            for (chunk in chunks) {
                val body = chunk.toRequestBody("application/octet-stream".toMediaType())
                offset = endpoint.appendBlobSession(deviceId, moduleId, session.sessionId, offset, body)
                val plaintextDone = if (cipherSize > 0L) {
                    ((plaintextSize.toDouble() * offset) / cipherSize).toLong().coerceAtMost(plaintextSize)
                } else {
                    plaintextSize
                }
                onProgress?.invoke(BlobProgress(bytesTransferred = plaintextDone, bytesTotal = plaintextSize))
            }
            // Surface any producer exception that closed the channel without throwing through
            // the iterator (rare but possible if encrypt finished and channel.close(cause) was
            // racing the natural close).
            producer.join()
        } catch (t: Throwable) {
            producer.cancelAndJoin()
            throw t
        }

        ciphertextDigest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Synchronous worker run on Dispatchers.IO inside [runInterruptible]. Reads plaintext from
     * [source], pushes it through Tink's encrypting OutputStream, accumulates the resulting
     * ciphertext into [MAX_CHUNK_BYTES]-sized chunks, and ships each chunk via [chunks].
     *
     * Updates [ciphertextDigest] with every byte that goes downstream so the caller can finalize
     * with the full-stream SHA-256.
     */
    private fun encryptIntoChunks(
        plaintextSource: Source,
        aad: ByteArray,
        chunks: SendChannel<ByteArray>,
        ciphertextDigest: MessageDigest,
    ) {
        val pending = okio.Buffer()
        val chunkingSink = object : Sink {
            override fun write(source: okio.Buffer, byteCount: Long) {
                var remaining = byteCount
                while (remaining > 0) {
                    val canFit = MAX_CHUNK_BYTES - pending.size
                    val toCopy = if (remaining < canFit) remaining else canFit
                    pending.write(source, toCopy)
                    remaining -= toCopy
                    if (pending.size >= MAX_CHUNK_BYTES) {
                        val chunk = pending.readByteArray(MAX_CHUNK_BYTES)
                        ciphertextDigest.update(chunk)
                        chunks.trySendBlocking(chunk).getOrThrow()
                    }
                }
            }
            override fun flush() = Unit
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }

        cipher.encrypt(plaintextSource, chunkingSink, aad)

        // Trailing bytes left in the buffer after Tink's final segment are emitted as one last
        // partial chunk. For zero-byte plaintext, Tink still writes a header + final tag so the
        // buffer is non-empty on a fresh stream.
        if (pending.size > 0) {
            val tail = pending.readByteArray()
            ciphertextDigest.update(tail)
            chunks.trySendBlocking(tail).getOrThrow()
        }
    }

    /**
     * Refresh the storage snapshot inside the error path. A refresh failure here must not
     * supplant the original upload error, so all non-cancellation exceptions are absorbed.
     */
    private suspend fun refreshStatusBestEffort(forceFresh: Boolean = false) {
        try {
            storageStatus.refresh(forceFresh)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "refreshStatusBestEffort() failed: ${e.message}" }
        }
    }

    private suspend fun abortSessionSafely(deviceId: DeviceId, moduleId: ModuleId, sessionId: String?) {
        sessionId ?: return
        // NonCancellable so a user-cancelled upload still issues the session abort — otherwise
        // the cancelled scope would skip the abort and leave the session lingering until idle GC.
        withContext(NonCancellable) {
            try {
                endpoint.abortBlobSession(deviceId, moduleId, sessionId)
            } catch (e: Exception) {
                log(TAG, WARN) { "abortSessionSafely(): Failed to abort session $sessionId: ${e.message}" }
            }
        }
    }

    private fun recordRecentSession(blobId: String, deviceId: DeviceId, sessionId: String, moduleId: ModuleId) {
        val now = Clock.System.now()
        recentSessions[blobId] = RecentSession(deviceId, sessionId, moduleId, now)
        // Lazy eviction — keeps memory bounded without a dedicated GC.
        recentSessions.entries.removeIf { now - it.value.finalizedAt > RECENT_SESSION_TTL }
    }

    override suspend fun abortPostFinalize(deviceId: DeviceId, moduleId: ModuleId, remoteRef: RemoteBlobRef) {
        val entry = recentSessions.remove(remoteRef.value) ?: return
        log(TAG, INFO) { "abortPostFinalize(${remoteRef.value}): aborting session ${entry.sessionId}" }
        abortSessionSafely(entry.deviceId, entry.moduleId, entry.sessionId)
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

    @AssistedFactory
    interface Factory {
        fun create(
            credentials: OctiServer.Credentials,
            endpoint: OctiServerEndpoint,
            storageStatus: StorageStatusProvider,
        ): OctiServerBlobStore
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "BlobStore")
        private const val MAX_CHUNK_BYTES = 1L * 1024 * 1024

        // How long the recent-session map remembers a finalized session for cancel cleanup. A
        // little above the server's COMPLETE_IDLE_TTL_SECONDS so we always have a useful handle
        // before the server reaps it on its own.
        private val RECENT_SESSION_TTL = 15.minutes

        // Stable substrings emitted by `BlobRoute.kt` when the upload size disagrees with what
        // the client declared. Captured here so the typed exception can be raised before the
        // generic `OctiServerHttpException` propagates. If either substring ever changes server
        // side, these tests catch it (`OctiServerBlobStoreTest`'s size-mismatch test exercises
        // both routes via fake endpoint responses).
        private const val SERVER_MSG_PATCH_SIZE_EXCEEDED = "Upload would exceed declared size"
        private const val SERVER_MSG_FINALIZE_INCOMPLETE = "Upload not complete:"

        private fun isSizeMismatchBody(body: String): Boolean =
            body.contains(SERVER_MSG_PATCH_SIZE_EXCEEDED) || body.contains(SERVER_MSG_FINALIZE_INCOMPLETE)
    }
}
