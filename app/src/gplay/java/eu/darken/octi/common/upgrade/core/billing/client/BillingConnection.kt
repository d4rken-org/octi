package eu.darken.octi.common.upgrade.core.billing.client

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.billing.BillingData
import eu.darken.octi.common.upgrade.core.billing.BillingManager.Companion.tryMapUserFriendly
import eu.darken.octi.common.upgrade.core.billing.Sku
import eu.darken.octi.common.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class BillingConnection(
    private val client: BillingClient,
    // Listener overlay from onPurchasesUpdated. Mutable + reconcilable: a stale record (e.g. an
    // isAutoRenewing=true event) must be prunable by a later conclusive query, or it would union
    // into billingData forever and keep the switch-to-IAP gate locked after the user cancelled.
    private val purchasesGlobal: MutableStateFlow<Collection<Purchase>>,
    private val freshObservations: MutableSharedFlow<FreshPurchases>,
    private val freshFailuresGlobal: MutableSharedFlow<Unit>,
    private val purchaseFailureEvents: Flow<BillingResult?>,
    private val listenerGeneration: () -> Long,
) {

    private val queryCacheIaps = MutableStateFlow<QueryCache?>(null)
    private val queryCacheSubs = MutableStateFlow<QueryCache?>(null)

    val billingData: Flow<BillingData> = combine(
        purchasesGlobal,
        queryCacheIaps.filterNotNull(),
        queryCacheSubs.filterNotNull(),
    ) { overlay, iapCache, subCache ->
        val combined = mutableSetOf<Purchase>()

        combined.addAll(iapCache.purchases)
        combined.addAll(subCache.purchases)
        combined.addAll(overlay.filter { it.purchaseState == PurchaseState.PURCHASED })

        BillingData(
            purchases = combined.sortedByDescending { it.purchaseTime },
            iapQueryOk = iapCache.querySucceeded,
            subQueryOk = subCache.querySucceeded,
        )
    }.setupCommonEventHandlers(TAG) { "billingData" }

    // Every conclusive fresh look at PURCHASED purchases (successful queries and push payloads),
    // tagged with provenance: only a full snapshot proves absence. The combined billingData state
    // is equality-deduped and can mix in stale listener data, so grace stamping must use THIS.
    val freshPurchases: Flow<FreshPurchases> = freshObservations

    // Failed attempts to get a fresh conclusive look (query errors). Consumed by UpgradeRepoGplay
    // to start the unconfirmed-episode clock — without this, a sustained Play outage would never
    // escalate the grace UI to its diagnostics stage.
    val freshFailures: Flow<Unit> = freshFailuresGlobal

    private data class QueryCache(
        val purchases: Collection<Purchase>,
        val querySucceeded: Boolean,
    )

    // Non-OK results from onPurchasesUpdated (e.g. async ITEM_ALREADY_OWNED after the Play sheet
    // opened). Consumed by a single persistent collector in UpgradeRepoGplay — not an event bus.
    val purchaseFailures: Flow<BillingResult> = purchaseFailureEvents.filterNotNull()

    // Serializes refreshes on this connection: the initial query, foreground refreshes, manual
    // restores, switch-gate verifications and already-owned recoveries may overlap, and an older
    // query completing late must not overwrite the cache with stale purchases.
    private val refreshLock = Mutex()

    private suspend fun queryPurchases(@BillingClient.ProductType type: String): Collection<Purchase> {
        val params = QueryPurchasesParams.newBuilder().apply {
            setProductType(type)
        }.build()
        val (billingResult, purchaseData) = client.queryPurchasesAsync(params)

        log(TAG) {
            "queryPurchases($type): code=${billingResult.isSuccess}, message=${billingResult.debugMessage}, purchaseData=${purchaseData}"
        }

        if (!billingResult.isSuccess) {
            log(TAG, WARN) { "queryPurchases() failed" }
            throw BillingClientException(billingResult)
        }

        return purchaseData
    }

    // Returns the freshly queried PURCHASED purchases (with provenance) so callers get a guaranteed
    // happens-before relation instead of racing the shared billingData replay caches after a
    // refresh. Tolerant of a single product-type failure: if either query finds a purchase we treat
    // that as authoritative, and only propagate an error when nothing was found AND a query failed —
    // so the caller can tell "not owned" apart from "couldn't verify".
    suspend fun refreshPurchases(): FreshPurchases = refreshLock.withLock {
        refreshPurchasesLocked()
    }

    private suspend fun refreshPurchasesLocked(): FreshPurchases = coroutineScope {
        log(TAG) { "refreshPurchases()" }
        val generationBefore = listenerGeneration()

        val iapJob = async { queryPurchasedProducts(BillingClient.ProductType.INAPP, queryCacheIaps) }
        val subJob = async { queryPurchasedProducts(BillingClient.ProductType.SUBS, queryCacheSubs) }
        val iaps = iapJob.await()
        val subs = subJob.await()
        log(TAG) { "Refreshed IAPs=${iaps.getOrNull()}, SUBs=${subs.getOrNull()}" }

        val combined = try {
            combinePurchaseResults(iaps, subs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            freshFailuresGlobal.tryEmit(Unit)
            throw e
        }

        val bothOk = iaps.isSuccess && subs.isSuccess
        reconcileListenerRecords(
            fresh = combined.purchases,
            generationBefore = generationBefore,
            // A conclusive both-type query proves absence for every product: listener records it
            // didn't return are stale (refunded, expired) and must not linger until restart.
            absenceProven = bothOk,
            absenceScope = { true },
        )

        // Absence is only proven when both type queries succeeded AND no purchase event raced the
        // queries: a racing event either flips the generation (downgrading this to presence-only),
        // or its own fresh emission follows ours and immediately re-stamps the confirmation.
        val isFullSnapshot = bothOk && listenerGeneration() == generationBefore
        val fresh = FreshPurchases(combined.purchases, isFullSnapshot)

        // A conclusive refresh is a fresh observation for the grace stamping, even when the result
        // equals the previous one and the state flows dedupe it away.
        freshObservations.tryEmit(fresh)

        fresh
    }

    // Strict SUBS-only verification query for the switch-to-IAP gate: errors propagate (the caller
    // fails closed), and the result is committed to the caches so the ownership UI heals from stale
    // renewal state (e.g. after the user cancelled the subscription in Play).
    suspend fun querySubscriptions(): Collection<Purchase> = refreshLock.withLock {
        log(TAG) { "querySubscriptions()" }
        val generationBefore = listenerGeneration()
        val subs = try {
            queryPurchases(BillingClient.ProductType.SUBS).filter { it.purchaseState == PurchaseState.PURCHASED }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            freshFailuresGlobal.tryEmit(Unit)
            throw e.tryMapUserFriendly()
        }

        queryCacheSubs.value = QueryCache(purchases = subs, querySucceeded = true)
        reconcileListenerRecords(
            fresh = subs,
            generationBefore = generationBefore,
            // A conclusive SUBS query proves absence for subscriptions only.
            absenceProven = true,
            absenceScope = { it.isKnownSub() },
        )

        // Presence-only provenance: a SUBS query proves nothing about the IAP.
        freshObservations.tryEmit(FreshPurchases(subs, isFullSnapshot = false))

        // Surviving listener records for known subs — empty after a non-racing conclusive query
        // (reconciliation pruned them). After a RACE the overlay keeps the NEWER record, which must
        // WIN over the older query row for its token: a just-arrived renewing subscription must not
        // be hidden by a stale no-longer-renewing query result and slip past the switch gate.
        val listenerSubs = purchasesGlobal.value.filter {
            it.purchaseState == PurchaseState.PURCHASED && it.isKnownSub()
        }
        val listenerTokens = listenerSubs.mapTo(mutableSetOf()) { it.purchaseToken }

        subs.filter { it.purchaseToken !in listenerTokens } + listenerSubs
    }

    private fun Purchase.isKnownSub(): Boolean = products.any { it == OurSku.Sub.PRO_UPGRADE.id }

    // Reconciles the listener overlay against a fresh query result — but ONLY when no purchase event
    // raced the query (generation unchanged, re-checked inside the update): a listener record
    // published mid-query is NEWER than the query result and must survive, or a just-renewed
    // subscription could be deleted by an older query and slip past the IAP gate. Without a race:
    // fresh records supersede same-token listener records (a stale in-session isAutoRenewing=true
    // must not coexist with newer query state forever), and when absence was proven, in-scope
    // listener records the query didn't return are dropped entirely (refunded/expired purchases
    // must not keep resurrecting until process restart).
    private fun reconcileListenerRecords(
        fresh: Collection<Purchase>,
        generationBefore: Long,
        absenceProven: Boolean,
        absenceScope: (Purchase) -> Boolean,
    ) {
        purchasesGlobal.update { current ->
            if (listenerGeneration() != generationBefore) return@update current
            current.filterNot { old ->
                fresh.any { it.purchaseToken == old.purchaseToken } || (absenceProven && absenceScope(old))
            }
        }
    }

    // Never throws except on cancellation, so a single failing product-type query doesn't cancel the
    // sibling query (or the coroutineScope). The exception is already user-friendly-mapped. On
    // failure the cache keeps its previous purchases (seeded empty if it had none) so the combined
    // billingData flow still emits — a persistently failing product type must not block the other
    // type's purchases or starve purchase acknowledgement.
    private suspend fun queryPurchasedProducts(
        @BillingClient.ProductType type: String,
        cache: MutableStateFlow<QueryCache?>,
    ): Result<Collection<Purchase>> = try {
        val purchased = queryPurchases(type).filter { it.purchaseState == PurchaseState.PURCHASED }
        cache.value = QueryCache(purchases = purchased, querySucceeded = true)
        Result.success(purchased)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        cache.value = QueryCache(
            purchases = cache.value?.purchases ?: emptyList(),
            querySucceeded = false,
        )
        Result.failure(e.tryMapUserFriendly())
    }

    suspend fun acknowledgePurchase(purchase: Purchase): BillingResult {
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()

        val ackResult = suspendCoroutine<BillingResult> { continuation ->
            client.acknowledgePurchase(ack) { continuation.resume(it) }
        }
        log(TAG) {
            "acknowledgePurchase(purchase=$purchase): code=${ackResult.responseCode}, message=${ackResult.debugMessage})"
        }

        if (!ackResult.isSuccess) {
            throw BillingClientException(ackResult)
        }
        return ackResult
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> {
        log(TAG) { "querySkus(skus=${skus.joinToString { it.print() }})..." }
        val productList = skus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder().apply {
                setProductId(sku.id)
                setProductType(
                    when (sku.type) {
                        Sku.Type.IAP -> BillingClient.ProductType.INAPP
                        Sku.Type.SUBSCRIPTION -> BillingClient.ProductType.SUBS
                    }
                )
            }.build()
        }

        val params = QueryProductDetailsParams.newBuilder().apply {
            setProductList(productList)
        }.build()

        val (result, details) = suspendCoroutine<Pair<BillingResult, Collection<ProductDetails>?>> { continuation ->
            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                continuation.resume(billingResult to queryResult.productDetailsList)
            }
        }

        log(TAG) {
            "querySkus(skus=${skus.joinToString { it.print() }}): code=${result.responseCode}, debug=${result.debugMessage}), skuDetails=$details"
        }
        // Make "Play withheld an offer" vs "we failed to match it" diagnosable from debug logs.
        details?.forEach { detail ->
            log(TAG) {
                "querySkus(): ${detail.productId} offers=${detail.subscriptionOfferDetails?.map { "${it.basePlanId}:${it.offerId}" }}"
            }
        }

        if (!result.isSuccess) throw BillingClientException(result)

        if (details.isNullOrEmpty()) {
            throw IllegalStateException("No details available for ${skus.joinToString { "${it.type}-${it.id}" }}")
        }

        return details
            .groupBy { it.productId }
            .mapNotNull { (key, details) ->
                val sku = skus
                    .single { it.id == key }
                val detail = details.single { it.productId == sku.id }

                SkuDetails(sku, detail)
            }
    }

    suspend fun launchBillingFlow(activity: Activity, sku: Sku, targetOffer: Sku.Subscription.Offer?): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, sku=$sku)" }
        if (sku.type == Sku.Type.SUBSCRIPTION) {
            requireNotNull(targetOffer) { "SUB skus require a target offer" }
        }

        val data = querySkus(sku).single { it.sku == sku }

        val params = BillingFlowParams.newBuilder().apply {
            val productDetail = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                setProductDetails(data.details)
                if (sku is Sku.Subscription && targetOffer != null) {
                    val offer = data.details.subscriptionOfferDetails!!.single {
                        targetOffer.matches(it)
                    }
                    setOfferToken(offer.offerToken)
                }
            }.build()
            setProductDetailsParamsList(listOf(productDetail))
        }.build()

        // launchBillingFlow must run on the main thread (documented BillingClient contract), and its
        // RETURNED result reports whether the flow could be launched at all (DEVELOPER_ERROR,
        // ITEM_ALREADY_OWNED, BILLING_UNAVAILABLE, ...) — failures arrive here, not as exceptions.
        // Throw like the other client calls do, so callers can surface them instead of silence.
        val result = withContext(Dispatchers.Main) {
            client.launchBillingFlow(activity, params)
        }
        log(TAG) {
            "launchBillingFlow(sku=$sku): code=${result.responseCode}, message=${result.debugMessage}"
        }
        if (!result.isSuccess) throw BillingClientException(result)

        return result
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "ClientConnection")

        // Combines the two product-type query results: a purchase found by either type is
        // authoritative; an error is only propagated when nothing was found, so callers can tell
        // "not owned" apart from "couldn't verify one product type". Pure and unit-tested.
        internal fun combinePurchaseResults(
            iaps: Result<Collection<Purchase>>,
            subs: Result<Collection<Purchase>>,
        ): BillingData {
            val found = iaps.getOrNull().orEmpty() + subs.getOrNull().orEmpty()
            if (found.isEmpty()) (iaps.exceptionOrNull() ?: subs.exceptionOrNull())?.let { throw it }
            return BillingData(
                purchases = found.sortedByDescending { it.purchaseTime },
                iapQueryOk = iaps.isSuccess,
                subQueryOk = subs.isSuccess,
            )
        }
    }
}
