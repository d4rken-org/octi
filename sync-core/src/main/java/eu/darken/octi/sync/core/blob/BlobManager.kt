package eu.darken.octi.sync.core.blob

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.BlobKey
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.RemoteBlobRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import kotlinx.coroutines.flow.first
import okio.Sink
import okio.Source
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Singleton
class BlobManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val blobStoreHubs: Set<@JvmSuppressWildcards BlobStoreHub>,
) {

    data class PutResult(
        val successful: Set<ConnectorId>,
        val perConnectorErrors: Map<ConnectorId, Throwable>,
        val remoteRefs: Map<ConnectorId, RemoteBlobRef> = emptyMap(),
    )

    /**
     * Per-(connector, blob) exponential backoff record. Advances through [BACKOFF_SCHEDULE]
     * on each failure, resets on success. Clock.System.now is called lazily inside the hot
     * put() loop so the map never holds timestamps older than the current wall-clock.
     */
    private data class BackoffState(val failureCount: Int, val nextAttemptAt: Instant)

    private val retryBackoff = ConcurrentHashMap<Pair<ConnectorId, BlobKey>, BackoffState>()
    private val quotaRefreshTrigger = MutableStateFlow(0)

    private val allStores: Flow<List<BlobStore>> = if (blobStoreHubs.isEmpty()) {
        flowOf(emptyList())
    } else {
        flow {
            emit(blobStoreHubs)
            kotlinx.coroutines.awaitCancellation()
        }.flatMapLatest { hubs ->
            if (hubs.isEmpty()) flowOf(emptyList())
            else combine(hubs.map { it.blobStores }) { arrays -> arrays.toList().flatten() }
        }
    }
        .setupCommonEventHandlers(TAG) { "allStores" }
        .shareLatest(scope + dispatcherProvider.Default)

    private val quotasFlow: Flow<Map<ConnectorId, BlobStoreQuota?>> = allStores
        .combine(quotaRefreshTrigger) { stores, _ -> stores }
        .flatMapLatest { stores ->
            flow {
                emit(stores.associate { it.connectorId to null })
                emit(fetchQuotas(stores))
            }
        }
        .setupCommonEventHandlers(TAG) { "quotas" }
        .shareLatest(scope + dispatcherProvider.Default)

    /**
     * Upload blob from a fresh [openSource] to all eligible connectors.
     * @param eligibleConnectors if null, use all currently configured stores
     * @param onProgress optional progress callback. Fired per-connector from each store's chunk
     *        hook. The callback receives the *slowest* connector's progress — two parallel uploads
     *        appear as a single monotonically advancing bar that only reaches 100% once both
     *        finish. Safe to call from multiple coroutines concurrently.
     * @return which connectors succeeded, per-connector errors, and per-connector remote refs
     */
    suspend fun put(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        openSource: () -> Source,
        metadata: BlobMetadata,
        eligibleConnectors: Set<ConnectorId>? = null,
        onProgress: BlobProgressCallback? = null,
    ): PutResult = kotlinx.coroutines.withContext(dispatcherProvider.IO) {
        val stores = allStores.first()
        val candidates = if (eligibleConnectors != null) {
            stores.filter { it.connectorId in eligibleConnectors }
        } else {
            stores
        }

        log(TAG, DEBUG) { "put(${blobKey.id}): ${candidates.size} candidate stores" }

        val successful = ConcurrentHashMap.newKeySet<ConnectorId>()
        val errors = ConcurrentHashMap<ConnectorId, Throwable>()
        val remoteRefs = ConcurrentHashMap<ConnectorId, RemoteBlobRef>()
        val aggregator: ProgressAggregator? = onProgress?.let {
            ProgressAggregator(expectedConnectors = candidates.size, downstream = it)
        }

        candidates.map { store ->
            async {
                try {
                    // Per-connector constraint check
                    val constraints = store.getConstraints()
                    if (constraints.maxFileBytes != null && metadata.size > constraints.maxFileBytes) {
                        log(TAG, INFO) { "put(${blobKey.id}): Skipping ${store.connectorId} — file too large (${metadata.size} > ${constraints.maxFileBytes})" }
                        errors[store.connectorId] = BlobFileTooLargeException(store.connectorId, constraints, metadata.size)
                        return@async
                    }

                    val quota = store.getQuota()
                    val maxTotalBytes = quota?.totalBytes ?: constraints.maxTotalBytes
                    val usedBytes = quota?.usedBytes ?: 0
                    if (maxTotalBytes != null && usedBytes + metadata.size > maxTotalBytes) {
                        log(TAG, INFO) { "put(${blobKey.id}): Skipping ${store.connectorId} — quota exceeded" }
                        errors[store.connectorId] = BlobQuotaExceededException(
                            quota ?: BlobStoreQuota(
                                connectorId = store.connectorId,
                                usedBytes = usedBytes,
                                totalBytes = maxTotalBytes,
                            ),
                            metadata.size,
                        )
                        return@async
                    }

                    val storeProgress: BlobProgressCallback? = aggregator?.forConnector(store.connectorId)

                    val remoteRef = openSource().use { source ->
                        store.put(deviceId, moduleId, blobKey, source, metadata, storeProgress)
                    }
                    successful.add(store.connectorId)
                    remoteRefs[store.connectorId] = remoteRef
                    retryBackoff.remove(store.connectorId to blobKey)
                    log(TAG, INFO) { "put(${blobKey.id}): Success on ${store.connectorId}" }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "put(${blobKey.id}): Failed on ${store.connectorId}: ${e.asLog()}" }
                    errors[store.connectorId] = e
                    recordFailure(store.connectorId, blobKey)
                }
            }
        }.awaitAll()

        if (successful.isNotEmpty()) {
            quotaRefreshTrigger.update { it + 1 }
        }

        PutResult(successful = successful, perConnectorErrors = errors, remoteRefs = remoteRefs)
    }

    /**
     * Download a blob to a fresh [openSink] from the first reachable candidate.
     * Iterates candidates in map iteration order; on failure, reopens the sink and tries the next.
     *
     * @param candidates per-connector locations: connector id → the remote reference stored for
     *                   that connector in `SharedFile.connectorRefs`.
     * @param onProgress optional progress callback fired as bytes arrive at the sink. Only the
     *        first successful candidate emits progress; failed candidates that bailed before
     *        writing leave the callback untouched.
     * @throws BlobNotFoundException if every candidate fails.
     */
    suspend fun get(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        candidates: Map<ConnectorId, RemoteBlobRef>,
        openSink: () -> Sink,
        onProgress: BlobProgressCallback? = null,
    ): BlobMetadata = kotlinx.coroutines.withContext(dispatcherProvider.IO) {
        val stores = allStores.first()
        val ordered = candidates.mapNotNull { (id, ref) ->
            stores.find { it.connectorId == id }?.let { store -> store to ref }
        }

        log(TAG, DEBUG) { "get(${blobKey.id}): ${ordered.size} candidate stores" }

        var lastError: Exception? = null
        for ((store, remoteRef) in ordered) {
            try {
                val meta = openSink().use { sink ->
                    store.get(deviceId, moduleId, blobKey, remoteRef, sink, onProgress)
                }
                log(TAG, INFO) { "get(${blobKey.id}): Success from ${store.connectorId}" }
                return@withContext meta
            } catch (e: Exception) {
                log(TAG, WARN) { "get(${blobKey.id}): Failed from ${store.connectorId}: ${e.message}" }
                lastError = e
            }
        }

        throw BlobNotFoundException(blobKey).also { it.initCause(lastError) }
    }

    /**
     * Aggregates per-connector upload progress into one downstream callback. The reported
     * fraction tracks the slowest connector so the bar cannot advance faster than the real
     * upload completes everywhere. Connectors that haven't reported yet count as 0%.
     */
    private class ProgressAggregator(
        private val expectedConnectors: Int,
        private val downstream: BlobProgressCallback,
    ) {
        private val perConnector = ConcurrentHashMap<ConnectorId, BlobProgress>()

        fun forConnector(connectorId: ConnectorId): BlobProgressCallback = { p ->
            perConnector[connectorId] = p
            emit()
        }

        private fun emit() {
            if (perConnector.isEmpty()) return
            val slowestFraction = if (perConnector.size < expectedConnectors) 0f
            else perConnector.values.minOf { it.fraction }
            val total = perConnector.values.maxOf { it.bytesTotal }
            val transferred = (total * slowestFraction).toLong()
            downstream(BlobProgress(bytesTransferred = transferred, bytesTotal = total))
        }
    }

    /**
     * Delete a blob across [targets] (connector id → remote reference). Returns the set of
     * connectors that reported success.
     */
    suspend fun delete(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        targets: Map<ConnectorId, RemoteBlobRef>,
    ): Set<ConnectorId> = kotlinx.coroutines.withContext(dispatcherProvider.IO) {
        val stores = allStores.first()
        val pairs = targets.mapNotNull { (id, ref) ->
            stores.find { it.connectorId == id }?.let { store -> store to ref }
        }

        log(TAG, DEBUG) { "delete(${blobKey.id}): ${pairs.size} target stores" }

        val successful = ConcurrentHashMap.newKeySet<ConnectorId>()
        pairs.map { (store, remoteRef) ->
            async {
                try {
                    store.delete(deviceId, moduleId, remoteRef)
                    successful.add(store.connectorId)
                    log(TAG, INFO) { "delete(${blobKey.id}): Success on ${store.connectorId}" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "delete(${blobKey.id}): Failed on ${store.connectorId}: ${e.message}" }
                }
            }
        }.awaitAll()

        if (successful.isNotEmpty()) {
            quotaRefreshTrigger.update { it + 1 }
        }

        successful
    }

    /**
     * List all remote refs across all stores for a device+module.
     */
    suspend fun listAll(deviceId: DeviceId, moduleId: ModuleId): Map<ConnectorId, Set<RemoteBlobRef>> {
        val stores = allStores.first()
        return stores.associate { store ->
            try {
                store.connectorId to store.list(deviceId, moduleId)
            } catch (e: Exception) {
                log(TAG, WARN) { "listAll(): Failed on ${store.connectorId}: ${e.message}" }
                store.connectorId to emptySet()
            }
        }
    }

    /**
     * IDs of all currently configured blob stores.
     */
    suspend fun configuredConnectorIds(): Set<ConnectorId> {
        return allStores.first().map { it.connectorId }.toSet()
    }

    suspend fun configuredConnectorsByIdString(): Map<String, ConnectorId> {
        return allStores.first().associate { it.connectorId.idString to it.connectorId }
    }

    /**
     * Quotas from all configured stores.
     */
    fun quotas(): Flow<Map<ConnectorId, BlobStoreQuota?>> = quotasFlow

    private suspend fun fetchQuotas(stores: List<BlobStore>): Map<ConnectorId, BlobStoreQuota?> = coroutineScope {
        stores.map { store ->
            async(dispatcherProvider.IO) {
                try {
                    store.connectorId to store.getQuota()
                } catch (e: Exception) {
                    log(TAG, WARN) { "quotas(): Failed on ${store.connectorId}: ${e.message}" }
                    store.connectorId to null
                }
            }
        }.awaitAll().toMap()
    }

    /**
     * Check if a retry for this connector+blob combination should be backed off.
     */
    fun isBackedOff(connectorId: ConnectorId, blobKey: BlobKey): Boolean {
        val state = retryBackoff[connectorId to blobKey] ?: return false
        return Clock.System.now() < state.nextAttemptAt
    }

    /**
     * Advances the failure count for [connectorId] x [blobKey] and sets the next attempt time
     * per [BACKOFF_SCHEDULE]. Called from the put() exception path.
     */
    private fun recordFailure(connectorId: ConnectorId, blobKey: BlobKey) {
        val key = connectorId to blobKey
        val prevCount = retryBackoff[key]?.failureCount ?: 0
        val newCount = prevCount + 1
        val delay = BACKOFF_SCHEDULE[(newCount - 1).coerceIn(0, BACKOFF_SCHEDULE.lastIndex)]
        retryBackoff[key] = BackoffState(
            failureCount = newCount,
            nextAttemptAt = Clock.System.now() + delay,
        )
    }

    companion object {
        private val TAG = logTag("Sync", "Blob", "Manager")
        // Failure N → BACKOFF_SCHEDULE[N-1] (clamped at the last entry).
        // Short first retry so transient blips (brief connectivity loss) recover quickly; longer
        // later delays so a persistently-broken connector isn't hammered.
        private val BACKOFF_SCHEDULE = listOf(
            30.seconds,
            2.minutes,
            10.minutes,
            30.minutes,
        )
    }
}
