package eu.darken.octi.syncs.octiserver.core

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.blob.StorageSnapshot
import eu.darken.octi.sync.core.blob.StorageStatus
import eu.darken.octi.sync.core.blob.StorageStatusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class OctiServerStorageStatusProvider @AssistedInject constructor(
    @Assisted override val connectorId: ConnectorId,
    @Assisted private val accountLabel: String?,
    @Assisted private val endpoint: OctiServerEndpoint,
    private val dispatcherProvider: DispatcherProvider,
) : StorageStatusProvider {

    private val _status = MutableStateFlow<StorageStatus>(
        StorageStatus.Loading(connectorId, lastKnown = null)
    )
    override val status: StateFlow<StorageStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private var lastFetchAt: Instant? = null

    override suspend fun refresh(forceFresh: Boolean) {
        if (!forceFresh && isFresh()) return
        mutex.withLock {
            // Double-checked TTL inside the lock so contending callers share one fetch.
            if (!forceFresh && isFresh()) return
            _status.update { StorageStatus.Loading(connectorId, lastKnown = it.lastKnown) }
            try {
                val storage = withContext(dispatcherProvider.IO) { endpoint.getAccountStorage() }
                val now = Clock.System.now()
                val snapshot = StorageSnapshot(
                    connectorId = connectorId,
                    accountLabel = accountLabel,
                    usedBytes = storage.usedBytes,
                    totalBytes = storage.accountQuotaBytes,
                    availableBytes = storage.availableBytes,
                    maxFileBytes = (storage.maxBlobBytes - CIPHERTEXT_OVERHEAD_BUFFER).coerceAtLeast(0),
                    perFileOverheadBytes = CIPHERTEXT_OVERHEAD_BUFFER,
                    updatedAt = now,
                )
                log(TAG, VERBOSE) {
                    "refresh(${connectorId.subtype}): used=${storage.usedBytes} quota=${storage.accountQuotaBytes} avail=${storage.availableBytes}"
                }
                _status.value = StorageStatus.Ready(connectorId, snapshot)
                lastFetchAt = now
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "refresh(${connectorId.subtype}) failed: ${e.message}" }
                _status.update { StorageStatus.Unavailable(connectorId, e, it.lastKnown) }
                // Cache the failure timestamp too — otherwise a transient outage triggers a refetch
                // on every subsequent access until success.
                lastFetchAt = Clock.System.now()
            }
        }
    }

    override fun invalidate() {
        lastFetchAt = null
    }

    private fun isFresh(): Boolean {
        val last = lastFetchAt ?: return false
        return Clock.System.now() - last < TTL
    }

    @AssistedFactory
    interface Factory {
        fun create(
            connectorId: ConnectorId,
            accountLabel: String?,
            endpoint: OctiServerEndpoint,
        ): OctiServerStorageStatusProvider
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "StorageStatus")
        private val TTL = 30.seconds

        // Conservative buffer for Tink AesGcmHkdfStreaming overhead. Mirrors
        // OctiServerBlobStore.CIPHERTEXT_OVERHEAD_BUFFER — duplicated to avoid a
        // sync-core → blob-store dependency leak; keep the two values in lock-step.
        const val CIPHERTEXT_OVERHEAD_BUFFER = 1024L
    }
}
