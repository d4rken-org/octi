package eu.darken.octi.syncs.gdrive.core

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

class GDriveStorageStatusProvider @AssistedInject constructor(
    @Assisted override val connectorId: ConnectorId,
    @Assisted private val connector: GDriveAppDataConnector,
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
            if (!forceFresh && isFresh()) return
            _status.update { StorageStatus.Loading(connectorId, lastKnown = it.lastKnown) }
            try {
                val raw = withContext(dispatcherProvider.IO) { connector.fetchStorageQuotaRaw() }
                val now = Clock.System.now()
                lastFetchAt = now
                if (raw == null) {
                    log(TAG, VERBOSE) { "refresh(${connectorId.account}): unsupported (no Drive quota)" }
                    _status.value = StorageStatus.Unsupported(connectorId)
                } else {
                    val snapshot = StorageSnapshot(
                        connectorId = connectorId,
                        accountLabel = connector.accountLabel,
                        usedBytes = raw.usedBytes,
                        totalBytes = raw.totalBytes,
                        availableBytes = (raw.totalBytes - raw.usedBytes).coerceAtLeast(0L),
                        maxFileBytes = null,
                        perFileOverheadBytes = 0L,
                        updatedAt = now,
                    )
                    log(TAG, VERBOSE) { "refresh(${connectorId.account}): used=${raw.usedBytes} total=${raw.totalBytes}" }
                    _status.value = StorageStatus.Ready(connectorId, snapshot)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "refresh(${connectorId.account}) failed: ${e.message}" }
                _status.update { StorageStatus.Unavailable(connectorId, e, it.lastKnown) }
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
            connector: GDriveAppDataConnector,
        ): GDriveStorageStatusProvider
    }

    companion object {
        private val TAG = logTag("Sync", "GDrive", "StorageStatus")
        private val TTL = 30.seconds
    }
}

/** Numeric storage figures from Drive. `null` from the connector means Drive does not report quota. */
data class GDriveStorageQuotaRaw(
    val usedBytes: Long,
    val totalBytes: Long,
)
