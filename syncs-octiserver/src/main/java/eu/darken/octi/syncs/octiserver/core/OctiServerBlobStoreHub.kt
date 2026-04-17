package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.blob.BlobStore
import eu.darken.octi.sync.core.blob.BlobStoreHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.plus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Exposes [BlobStore]s only for OctiServer connectors whose server is currently known to
 * support the blob API. Capability is cached with TTL so an in-place server upgrade
 * (legacy → blob-capable) is picked up without an app restart.
 *
 * TTLs:
 * - `SUPPORTED`: 1 h — blob support doesn't regress in practice.
 * - `LEGACY` / `UNKNOWN`: 10 min — re-probe frequently so upgrades take effect quickly.
 */
@Singleton
class OctiServerBlobStoreHub @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val octiServerHub: OctiServerHub,
    private val blobStoreFactory: OctiServerBlobStore.Factory,
    private val endpointFactory: OctiServerEndpoint.Factory,
) : BlobStoreHub {

    private data class CapabilityEntry(
        val capabilities: OctiServerCapabilities,
        val cachedAt: Instant,
    )

    private val storeCache = ConcurrentHashMap<ConnectorId, Pair<OctiServerConnector, OctiServerBlobStore>>()
    private val capabilityCache = ConcurrentHashMap<ConnectorId, CapabilityEntry>()
    private val refreshTrigger = MutableStateFlow(0)

    private val periodicTick: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(NEGATIVE_TTL)
        }
    }

    override val blobStores: Flow<Collection<BlobStore>> = combine(
        octiServerHub.connectors,
        refreshTrigger,
        periodicTick,
    ) { connectors, _, _ -> connectors }
        .map { connectors -> resolveStores(connectors) }
        // Suppress re-emissions that don't change the effective store set — otherwise
        // the periodic TTL tick propagates through BlobManager.quotasFlow as a
        // placeholder-then-refetch cycle every NEGATIVE_TTL, causing quota UI flicker.
        .distinctUntilChangedBy { stores -> stores.map { it.connectorId }.toSet() }
        .setupCommonEventHandlers(TAG) { "blobStores" }
        .shareLatest(scope + dispatcherProvider.Default)

    private suspend fun resolveStores(connectors: Collection<eu.darken.octi.sync.core.SyncConnector>): List<BlobStore> {
        val activeIds = connectors.map { it.identifier }.toSet()
        storeCache.keys.removeIf { it !in activeIds }
        capabilityCache.keys.removeIf { it !in activeIds }

        return connectors.mapNotNull { connector ->
            val id = connector.identifier
            val octiConnector = connector as? OctiServerConnector ?: return@mapNotNull null

            val capabilities = getOrResolveCapabilities(octiConnector)
            if (capabilities.blobSupport != OctiServerCapabilities.BlobSupport.SUPPORTED) {
                log(TAG) { "resolveStores(): $id capability=${capabilities.blobSupport}, excluding from blob stores" }
                storeCache.remove(id)
                return@mapNotNull null
            }

            storeCache[id]?.takeIf { (cachedConnector, _) -> cachedConnector === octiConnector }?.second ?: run {
                try {
                    val endpoint = endpointFactory.create(octiConnector.credentials.serverAdress).also {
                        it.setCredentials(octiConnector.credentials)
                    }
                    blobStoreFactory.create(octiConnector.credentials, endpoint).also { store ->
                        storeCache[id] = octiConnector to store
                    }
                } catch (e: IllegalArgumentException) {
                    // Legacy SIV keyset — no blob support on the client side even if server has it
                    log(TAG, INFO) { "Skipping blob store for $id: ${e.message}" }
                    storeCache.remove(id)
                    null
                }
            }
        }
    }

    private suspend fun getOrResolveCapabilities(connector: OctiServerConnector): OctiServerCapabilities {
        val id = connector.identifier
        val now = Clock.System.now()
        capabilityCache[id]?.let { entry ->
            if (!isStale(entry, now)) return entry.capabilities
        }

        val resolved = try {
            val endpoint = endpointFactory.create(connector.credentials.serverAdress).also {
                it.setCredentials(connector.credentials)
            }
            endpoint.resolveCapabilities()
        } catch (e: Exception) {
            log(TAG, WARN) { "getOrResolveCapabilities($id): probe failed, treating as UNKNOWN: ${e.message}" }
            OctiServerCapabilities(blobSupport = OctiServerCapabilities.BlobSupport.UNKNOWN)
        }
        capabilityCache[id] = CapabilityEntry(resolved, now)
        return resolved
    }

    private fun isStale(entry: CapabilityEntry, now: Instant): Boolean {
        val ttl: Duration = when (entry.capabilities.blobSupport) {
            OctiServerCapabilities.BlobSupport.SUPPORTED -> POSITIVE_TTL
            OctiServerCapabilities.BlobSupport.LEGACY,
            OctiServerCapabilities.BlobSupport.UNKNOWN -> NEGATIVE_TTL
        }
        return (now - entry.cachedAt) > ttl
    }

    override suspend fun owns(connectorId: ConnectorId): Boolean = octiServerHub.owns(connectorId)

    /**
     * Returns the cached capability if still fresh, or null if absent/stale.
     * Callers should follow up with [OctiServerEndpoint.resolveCapabilities] + [memoizeCapabilities]
     * when null is returned.
     */
    fun getCapabilities(connectorId: ConnectorId): OctiServerCapabilities? {
        val entry = capabilityCache[connectorId] ?: return null
        if (isStale(entry, Clock.System.now())) return null
        return entry.capabilities
    }

    /**
     * Record a capability observation made outside the hub (e.g. a 404/405 response during a
     * `PUT` commit). Triggers re-emission of [blobStores] so a newly-discovered LEGACY entry
     * retires its store right away.
     */
    fun memoizeCapabilities(connectorId: ConnectorId, capabilities: OctiServerCapabilities) {
        capabilityCache[connectorId] = CapabilityEntry(capabilities, Clock.System.now())
        refreshTrigger.update { it + 1 }
    }

    companion object {
        private val TAG = logTag("Sync", "OctiServer", "BlobStore", "Hub")
        private val NEGATIVE_TTL = 10.minutes
        private val POSITIVE_TTL = 1.hours
    }
}
