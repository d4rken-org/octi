package eu.darken.octi.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.datastore.value
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.error.asErrorDialogBuilder
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.UpgradeRepo
import eu.darken.octi.common.upgrade.core.billing.BillingData
import eu.darken.octi.common.upgrade.core.billing.BillingManager
import eu.darken.octi.common.upgrade.core.billing.FreshBillingData
import eu.darken.octi.common.upgrade.core.billing.PurchasedSku
import eu.darken.octi.common.upgrade.core.billing.Sku
import eu.darken.octi.common.upgrade.core.billing.SkuDetails
import eu.darken.octi.common.upgrade.core.billing.UserCanceledBillingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
    private val clock: Clock,
) : UpgradeRepo {

    override val mainWebsite: String = SITE

    private fun nowMs(): Long = clock.now().toEpochMilliseconds()

    // Serializes the check-then-write anchor/episode logic: concurrent fresh observations (init
    // collector, failure events, direct restores) must not interleave between reading the current
    // anchor and stamping the new one.
    private val proStateLock = Mutex()

    // Declared before init: the init collector below flips it, and under an eager (Unconfined)
    // subscriber it can run during construction before a later-declared field would initialize.
    private val _autoRestoreInProgress = MutableStateFlow(false)

    init {
        // Fresh-provenance grace stamping: freshBillingData carries every conclusive observation
        // (successful queries + push payloads) with provenance, unlike the reactive upgradeInfo map
        // which re-runs on replayed shared-flow data and therefore writes nothing.
        billingManager.freshBillingData
            .onEach { fresh ->
                try {
                    recordProState(fresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed DataStore write must not kill this process-lifetime collector.
                    log(TAG, WARN) { "Failed to record pro state: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "proStateRecorder" }
            .launchIn(scope)

        // Failed fresh-data attempts (query errors) start the unconfirmed-episode clock. Most of
        // these failures are swallowed by their pipelines (logged, retried later), so without this
        // collector a sustained Play outage would never age the grace presentation from
        // "confirming…" into its diagnostics stage.
        billingManager.refreshFailures
            .onEach {
                try {
                    proStateLock.withLock { billingCache.recordProUnconfirmed(nowMs()) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Failed to record unconfirmed state: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "unconfirmedRecorder" }
            .launchIn(scope)

        // Async variant of the launch-result ITEM_ALREADY_OWNED case: Play told us mid-flow that the
        // user already owns it. Reconcile silently — Play shows its own UI for purchase-sheet
        // failures, so no app-side dialog here.
        billingManager.purchaseFailures
            .filter { it.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED }
            .onEach {
                log(TAG, INFO) { "Async already-owned event -> restoring purchase" }
                _autoRestoreInProgress.value = true
                try {
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT) { restorePurchaseNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Async already-owned restore failed: ${e.asLog()}" }
                } finally {
                    _autoRestoreInProgress.value = false
                }
            }
            .setupCommonEventHandlers(TAG) { "asyncAlreadyOwned" }
            .launchIn(scope)
    }

    // True while the invisible already-owned reconciliation runs. Exposed so the UI can hold its buy
    // buttons disabled during it — that restore is off the VM's operation guard, so without this a
    // second purchase could be launched on top of the one being reconciled.
    val autoRestoreInProgress: Flow<Boolean> = _autoRestoreInProgress

    // Grace is time-based, but billingData is equality-deduped state kept hot by a process-lifetime
    // subscriber — without this deadline tick, a lapsed grace window would keep isPro=true until the
    // next distinct billing emission or a process restart, and every non-screen consumer
    // (UpgradeEntitlementObserver, widgets) would keep seeing an expired entitlement.
    private val graceDeadlineTick: Flow<Unit> = billingCache.lastProStateAt.flow
        .flatMapLatest { lastProAt ->
            flow {
                emit(Unit)
                if (lastProAt > 0L) {
                    val remaining = lastProAt + graceWindow().inWholeMilliseconds - nowMs()
                    if (remaining > 0) {
                        delay(remaining)
                        emit(Unit)
                    }
                }
            }
        }

    override val upgradeInfo: Flow<Info> = combine(
        billingManager.billingData
            .map<BillingData, BillingData?> { it }
            .onStart { emit(null) },
        graceDeadlineTick,
    ) { data, _ -> data }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { data: BillingData? -> data.toUpgradeInfo() }
        .distinctUntilChanged()
        .catch {
            // Ignore Google Play errors if the last pro state was recent
            val now = nowMs()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "Catch: now=$now, lastProStateAt=$lastProStateAt, error=$it" }
            if ((now - lastProStateAt).let { d -> d in 0..<graceWindow().inWholeMilliseconds }) {
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                throw it
            }
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeout = 3.seconds, replayExpiration = Duration.ZERO), replay = 1)

    // True once we've ever confirmed a (known) pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or a switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        .distinctUntilChanged()

    // Start of the current "fresh data can't confirm Pro" episode (0 = none open). Drives the
    // two-stage grace UI: calm confirmation phase first, diagnostics once the episode has aged.
    val proUnconfirmedSince: Flow<Long> = billingCache.proUnconfirmedAt.flow

    // True once any fresh billing observation arrived this process. The pre-reconciliation empty
    // purchase state must not enable purchase actions — an owner on a fresh install would briefly
    // look free and could buy the other product on top of what they already own.
    val isSettled: Flow<Boolean> = billingManager.freshBillingData
        .map { true }
        .onStart { emit(false) }
        .distinctUntilChanged()

    // Strict SUBS-only ownership check for the switch-to-IAP gate. Errors propagate — a subscriber
    // whose renewal state can't be verified must not be allowed to double-buy.
    suspend fun queryCurrentSubscriptions(): Collection<Purchase> = billingManager.querySubscriptions()

    fun launchBillingFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        scope.launch {
            try {
                launchBillingFlowNow(activity, sku, offer)
            } catch (e: UserCanceledBillingException) {
                log(TAG) { "User canceled the billing flow." }
            } catch (e: Exception) {
                log(TAG) { "launchBillingFlowNow failed:${e.asLog()}" }
                withContext(dispatcherProvider.Main) {
                    e.asErrorDialogBuilder(activity).show()
                }
            }
        }
    }

    // Suspending launch: the caller's coroutine stays alive until the Play sheet has been launched
    // (or failed), so a single-flight operation guard can cover the whole window instead of racing a
    // fire-and-forget launch.
    suspend fun launchBillingFlowNow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        log(TAG) { "launchBillingFlowNow($activity,$sku)" }
        billingManager.startIapFlow(activity, sku, offer)
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingManager.querySkus(*skus)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        try {
            val fresh = withTimeout(REFRESH_TIMEOUT) { billingManager.refresh() }
            recordProState(fresh)
        } catch (e: TimeoutCancellationException) {
            log(TAG, ERROR) { "Background refresh timed out" }
            recordUnconfirmedSafely()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background refresh: swallow-and-log so callers like the widget config screens aren't
            // affected. The explicit restore path uses restorePurchaseNow(), which surfaces errors.
            log(TAG, ERROR) { "Background refresh failed: ${e.asLog()}" }
            recordUnconfirmedSafely()
        }
    }

    // A refresh/restore that failed or timed out is a conclusive "couldn't confirm Pro" — start the
    // unconfirmed-episode clock so the grace UI can escalate to diagnostics after 24h even when the
    // failure never reaches the connection-level freshFailures signal (e.g. connection setup failed,
    // or the query hung until timeout). No-op unless we were previously Pro (guarded in the cache).
    private suspend fun recordUnconfirmedSafely() {
        try {
            proStateLock.withLock { billingCache.recordProUnconfirmed(nowMs()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to record unconfirmed state: ${e.asLog()}" }
        }
    }

    // Explicit "Restore purchase": query Play now and evaluate pro from the returned data in the same
    // coroutine (real happens-before), so we never read a stale upgradeInfo replay. Billing errors
    // propagate so the caller can distinguish "not owned" from "Play unavailable".
    suspend fun restorePurchaseNow(): Info {
        log(TAG) { "restorePurchaseNow()" }
        return try {
            val fresh = billingManager.refresh()
            recordProState(fresh)
            fresh.data.toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's catch: a transient Play error while we were pro recently
            // keeps us pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            val now = nowMs()
            val lastProStateAt = billingCache.lastProStateAt.value()
            if ((now - lastProStateAt).let { d -> d in 0..<graceWindow().inWholeMilliseconds }) {
                log(TAG, VERBOSE) { "Restore hit a Play error but we were pro recently -> grace" }
                // The failed restore is a conclusive "couldn't confirm" — open the episode so the
                // grace UI escalates to diagnostics after 24h.
                recordUnconfirmedSafely()
                Info(gracePeriod = true, billingData = null)
            } else {
                throw e
            }
        }
    }

    // Shared pro/grace mapping used by both the reactive upgradeInfo flow and restorePurchaseNow().
    // Only relinquishes pro state if we haven't had it for a while (grace period). READ-ONLY: this
    // runs on replayed shared-flow data too, so it must never stamp the grace cache — see
    // recordProState(). An unrecognized purchase neither counts as pro nor suppresses active grace.
    private suspend fun BillingData?.toUpgradeInfo(): Info {
        val now = nowMs()
        val lastProStateAt = billingCache.lastProStateAt.value()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        val info = Info(billingData = this)
        return when {
            preferredProSku(info.upgrades) != null -> info

            (now - lastProStateAt).let { d -> d in 0..<graceWindow().inWholeMilliseconds } -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> info
        }
    }

    // Persists what fresh data told us about Pro ownership. Callers must only pass FRESH data
    // (returned query results, or new emissions seen by the init collector) — never replayed flow
    // data. A confirmed purchase stamps the anchor and atomically closes any unconfirmed episode; a
    // full snapshot WITHOUT a pro purchase conclusively failed to confirm and starts the episode
    // clock; presence-only data without a purchase proves nothing either way. The permanent IAP wins
    // as anchor when both are owned, and an IAP anchor is sticky: purchase data may lack the IAP
    // because that query failed or was out of scope (SUBS-only verification), and a subscription seen
    // in the meantime must not shrink the 30d window of an owner whose IAP was never disproven.
    private suspend fun recordProState(fresh: FreshBillingData) {
        val upgrades = Info(billingData = fresh.data).upgrades
        val preferred = preferredProSku(upgrades)
        proStateLock.withLock {
            when {
                preferred != null -> {
                    val anchorIsIap = OurSku.PRO_SKUS.singleOrNull {
                        it.id == billingCache.lastProStateSku.value()
                    }?.type == Sku.Type.IAP
                    val anchorSku = preferred
                        .takeIf { it.type == Sku.Type.IAP || !anchorIsIap }
                        ?.id
                    billingCache.stampLastProState(anchorSku, nowMs())
                }

                fresh.isFullSnapshot -> {
                    billingCache.recordProUnconfirmed(nowMs())
                }
            }
        }
    }

    // Grace window depends on what was last owned: a permanent one-time purchase gets a long window,
    // a subscription (or an unknown/legacy last SKU) gets the short default.
    private suspend fun graceWindow(): Duration {
        val lastSku = billingCache.lastProStateSku.value()
        val type = OurSku.PRO_SKUS.singleOrNull { it.id == lastSku }?.type
        return if (type == Sku.Type.IAP) PRO_GRACE_PERIOD_IAP else PRO_GRACE_PERIOD
    }

    data class Info(
        val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY

        val upgrades: Collection<PurchasedSku> = billingData?.purchases
            ?.map { purchase ->
                purchase.products.mapNotNull { productId ->
                    val sku = OurSku.PRO_SKUS.singleOrNull { it.id == productId }
                    if (sku == null) {
                        log(TAG, ERROR) { "Unknown product: $productId ($purchase)" }
                        return@mapNotNull null
                    } else {
                        log(TAG) { "Mapped $productId to $sku ($purchase)" }
                    }
                    PurchasedSku(sku, purchase)
                }
            }
            ?.flatten()
            ?: emptySet()

        override val isPro: Boolean = upgrades.isNotEmpty() || gracePeriod

        override val upgradedAt: Instant? = upgrades
            .maxByOrNull { it.purchase.purchaseTime }
            ?.let { Instant.fromEpochMilliseconds(it.purchase.purchaseTime) }
    }


    companion object {
        private const val SITE = "https://play.google.com/store/apps/details?id=eu.darken.octi"

        // Keep paying users pro through transient empty/failed Play Billing responses. A permanent
        // one-time purchase should almost never be dropped on a hiccup, so it gets a long window; a
        // subscription legitimately lapses, so it keeps the short one. PRO_GRACE_PERIOD is the
        // subscription/default window (also used when the last-owned SKU is unknown/legacy).
        internal val PRO_GRACE_PERIOD = 7.days
        internal val PRO_GRACE_PERIOD_IAP = 30.days
        private val REFRESH_TIMEOUT = 15.seconds
        private val RESTORE_ON_OWNED_TIMEOUT = 15.seconds
        val TAG: String = logTag("Upgrade", "Gplay", "Repo")

        // The SKU whose grace window applies when several are owned: the permanent one-time purchase
        // wins over a subscription (purchases are time-sorted, so firstOrNull alone isn't enough).
        // null when no known pro SKU is owned.
        internal fun preferredProSku(upgrades: Collection<PurchasedSku>): Sku? =
            upgrades.firstOrNull { it.sku.type == Sku.Type.IAP }?.sku ?: upgrades.firstOrNull()?.sku
    }
}
