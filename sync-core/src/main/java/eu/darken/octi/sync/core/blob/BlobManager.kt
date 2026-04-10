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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
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
    )

    private val retryBackoff = ConcurrentHashMap<Pair<ConnectorId, BlobKey>, Instant>()

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

    /**
     * Upload blob from [payloadFile] to all eligible connectors. Each backend opens
     * its own fresh read from the file — the file must exist for the duration of this call.
     * @param eligibleConnectors if null, use all currently configured stores
     * @return which connectors succeeded and which failed
     */
    suspend fun put(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        payloadFile: Path,
        metadata: BlobMetadata,
        eligibleConnectors: Set<ConnectorId>? = null,
    ): PutResult = withContext(dispatcherProvider.IO) {
        val stores = allStores.first()
        val candidates = if (eligibleConnectors != null) {
            stores.filter { it.connectorId in eligibleConnectors }
        } else {
            stores
        }

        log(TAG, DEBUG) { "put(${blobKey.id}): ${candidates.size} candidate stores" }

        val successful = ConcurrentHashMap.newKeySet<ConnectorId>()
        val errors = ConcurrentHashMap<ConnectorId, Throwable>()

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
                    if (quota != null && constraints.maxTotalBytes != null && quota.usedBytes + metadata.size > constraints.maxTotalBytes) {
                        log(TAG, INFO) { "put(${blobKey.id}): Skipping ${store.connectorId} — quota exceeded" }
                        errors[store.connectorId] = BlobQuotaExceededException(quota, metadata.size)
                        return@async
                    }

                    store.put(deviceId, moduleId, blobKey, payloadFile, metadata)
                    successful.add(store.connectorId)
                    retryBackoff.remove(store.connectorId to blobKey)
                    log(TAG, INFO) { "put(${blobKey.id}): Success on ${store.connectorId}" }
                } catch (e: Exception) {
                    log(TAG, ERROR) { "put(${blobKey.id}): Failed on ${store.connectorId}: ${e.asLog()}" }
                    errors[store.connectorId] = e
                    retryBackoff[store.connectorId to blobKey] = Clock.System.now() + RETRY_BACKOFF
                }
            }
        }.awaitAll()

        PutResult(successful = successful, perConnectorErrors = errors)
    }

    /**
     * Download blob to [destinationFile] from the first available candidate.
     * Tries candidates in order; if one fails, truncates and tries the next.
     * @throws BlobNotFoundException if all candidates fail
     */
    suspend fun get(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        candidates: Set<ConnectorId>,
        destinationFile: Path,
    ): BlobMetadata = withContext(dispatcherProvider.IO) {
        val stores = allStores.first()
        val orderedStores = candidates.mapNotNull { id -> stores.find { it.connectorId == id } }

        log(TAG, DEBUG) { "get(${blobKey.id}): ${orderedStores.size} candidate stores" }

        var lastError: Exception? = null
        for (store in orderedStores) {
            try {
                val meta = store.get(deviceId, moduleId, blobKey, destinationFile)
                log(TAG, INFO) { "get(${blobKey.id}): Success from ${store.connectorId}" }
                return@withContext meta
            } catch (e: Exception) {
                log(TAG, WARN) { "get(${blobKey.id}): Failed from ${store.connectorId}: ${e.message}" }
                lastError = e
                // Clean up partial download
                try {
                    FileSystem.SYSTEM.delete(destinationFile)
                } catch (_: Exception) {
                    // Best effort cleanup
                }
            }
        }

        throw BlobNotFoundException(blobKey).also { it.initCause(lastError) }
    }

    /**
     * Delete blob across all candidate connectors. Returns the set that succeeded.
     */
    suspend fun delete(
        deviceId: DeviceId,
        moduleId: ModuleId,
        blobKey: BlobKey,
        candidates: Set<ConnectorId>,
    ): Set<ConnectorId> = withContext(dispatcherProvider.IO) {
        val stores = allStores.first()
        val targetStores = candidates.mapNotNull { id -> stores.find { it.connectorId == id } }

        log(TAG, DEBUG) { "delete(${blobKey.id}): ${targetStores.size} target stores" }

        val successful = ConcurrentHashMap.newKeySet<ConnectorId>()
        targetStores.map { store ->
            async {
                try {
                    store.delete(deviceId, moduleId, blobKey)
                    successful.add(store.connectorId)
                    log(TAG, INFO) { "delete(${blobKey.id}): Success on ${store.connectorId}" }
                } catch (e: Exception) {
                    log(TAG, WARN) { "delete(${blobKey.id}): Failed on ${store.connectorId}: ${e.message}" }
                }
            }
        }.awaitAll()

        successful
    }

    /**
     * List all blob keys across all stores for a device+module.
     */
    suspend fun listAll(deviceId: DeviceId, moduleId: ModuleId): Map<ConnectorId, Set<BlobKey>> {
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

    /**
     * Quotas from all configured stores.
     */
    fun quotas(): Flow<Map<ConnectorId, BlobStoreQuota?>> = allStores.map { stores ->
        stores.associate { store ->
            try {
                store.connectorId to store.getQuota()
            } catch (e: Exception) {
                log(TAG, WARN) { "quotas(): Failed on ${store.connectorId}: ${e.message}" }
                store.connectorId to null
            }
        }
    }

    /**
     * Check if a retry for this connector+blob combination should be backed off.
     */
    fun isBackedOff(connectorId: ConnectorId, blobKey: BlobKey): Boolean {
        val backoffUntil = retryBackoff[connectorId to blobKey] ?: return false
        return Clock.System.now() < backoffUntil
    }

    companion object {
        private val TAG = logTag("Sync", "Blob", "Manager")
        private val RETRY_BACKOFF = 5.minutes
    }
}
