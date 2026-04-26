package eu.darken.octi.sync.core.blob

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public storage-status facade. Aggregates per-connector [StorageStatusProvider]s exposed by
 * [BlobStoreHub]s. Both the file-share UI and the sync-list UI project from this; pre-flight in
 * [BlobManager] reads each provider directly via the owning [BlobStore].
 *
 * Auto-initial-refresh: when a new provider appears in [statuses], the manager kicks off a
 * `refresh(false)` so the initial `Loading(lastKnown = null)` resolves to Ready/Unavailable/Unsupported
 * without an external trigger. The TTL inside the provider keeps subsequent reads cheap.
 */
@Singleton
class StorageStatusManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val blobStoreHubs: Set<@JvmSuppressWildcards BlobStoreHub>,
) {

    private val rawStores: Flow<List<BlobStore>> = if (blobStoreHubs.isEmpty()) {
        flowOf(emptyList())
    } else {
        flow {
            emit(blobStoreHubs)
            kotlinx.coroutines.awaitCancellation()
        }.flatMapLatest { hubs ->
            if (hubs.isEmpty()) flowOf(emptyList())
            else combine(hubs.map { it.blobStores }) { arrays -> arrays.toList().flatten() }
        }
            .setupCommonEventHandlers(TAG) { "rawStores" }
            .shareLatest(scope + dispatcherProvider.Default)
    }

    init {
        // Auto-initial-refresh: every newly-appearing provider gets a single best-effort refresh
        // so the initial Loading state resolves without requiring an external nudge. Already-cached
        // providers no-op via TTL. Failures are absorbed inside the provider as Unavailable.
        scope.launch(dispatcherProvider.Default) {
            val seen = mutableSetOf<ConnectorId>()
            rawStores.collect { stores ->
                val current = stores.map { it.connectorId }.toSet()
                seen.retainAll(current)
                stores.forEach { store ->
                    if (seen.add(store.connectorId)) {
                        launch {
                            try {
                                store.storageStatus.refresh(forceFresh = false)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                log(TAG, WARN) { "auto-initial-refresh(${store.connectorId}) failed: ${e.message}" }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Map of all currently-known connector storage statuses. Includes paused connectors with
     * their last-known snapshot (no fetch happens while paused). Consumers filter as appropriate
     * (file-share screen hides paused; sync-list shows all).
     */
    val statuses: Flow<Map<ConnectorId, StorageStatus>> = rawStores
        .flatMapLatest { stores ->
            if (stores.isEmpty()) flowOf(emptyMap())
            else combine(stores.map { it.storageStatus.status }) { arr ->
                arr.associateBy { it.connectorId }
            }
        }
        .setupCommonEventHandlers(TAG) { "statuses" }
        .shareLatest(scope + dispatcherProvider.Default)

    /**
     * IDs of all currently-configured connectors regardless of [StorageStatus] phase. Used by
     * dashboard / file-share availability checks where "configured" means a provider exists,
     * independent of whether its first fetch has completed.
     */
    val configuredConnectorIds: Flow<Set<ConnectorId>> = rawStores
        .map { stores -> stores.map { it.connectorId }.toSet() }
        .setupCommonEventHandlers(TAG) { "configuredConnectorIds" }
        .shareLatest(scope + dispatcherProvider.Default)

    /** Force-refresh a single connector. No-op if the connector is not known. */
    suspend fun refresh(connectorId: ConnectorId, forceFresh: Boolean = false) {
        currentStores().firstOrNull { it.connectorId == connectorId }
            ?.storageStatus
            ?.refresh(forceFresh)
    }

    /** Refresh every known connector in parallel. Used by the file-share screen's "Sync now" button. */
    suspend fun refreshAll(forceFresh: Boolean = false) = coroutineScope {
        currentStores().map { store ->
            async {
                try {
                    store.storageStatus.refresh(forceFresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "refreshAll(${store.connectorId}) failed: ${e.message}" }
                }
            }
        }.awaitAll()
        Unit
    }

    /**
     * Invalidate the listed providers and force-refresh in parallel. Called by [FileShareService]
     * and [eu.darken.octi.modules.files.core.BlobMaintenance] after a module commit succeeds — the
     * blob-write changed the server-side numbers, so the cache is stale.
     */
    suspend fun invalidateAndRefresh(connectorIds: Set<ConnectorId>) = coroutineScope {
        if (connectorIds.isEmpty()) return@coroutineScope
        val targets = currentStores().filter { it.connectorId in connectorIds }
        targets.forEach { it.storageStatus.invalidate() }
        targets.map { store ->
            async {
                try {
                    store.storageStatus.refresh(forceFresh = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "invalidateAndRefresh(${store.connectorId}) failed: ${e.message}" }
                }
            }
        }.awaitAll()
        Unit
    }

    private suspend fun currentStores(): List<BlobStore> = rawStores.first()

    companion object {
        private val TAG = logTag("Sync", "Storage", "StatusManager")
    }
}
