package eu.darken.octi.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
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
import eu.darken.octi.common.upgrade.core.billing.PurchasedSku
import eu.darken.octi.common.upgrade.core.billing.Sku
import eu.darken.octi.common.upgrade.core.billing.SkuDetails
import eu.darken.octi.common.upgrade.core.billing.UserCanceledBillingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
) : UpgradeRepo {

    override val mainWebsite: String = SITE

    init {
        // Fresh-provenance grace stamping: this collector subscribes once at construction (eager,
        // process start) and never re-subscribes, so every value it sees was produced in this
        // process moments before — unlike the reactive upgradeInfo map, which re-runs on replayed
        // shared-flow data every time the UI resubscribes and therefore writes nothing anymore.
        // This is what keeps a purchase completion stamping the grace cache.
        billingManager.billingData
            .distinctUntilChanged()
            .onEach { data ->
                try {
                    recordProState(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed DataStore write must not kill this process-lifetime collector.
                    log(TAG, WARN) { "Failed to record pro state: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "proStateRecorder" }
            .launchIn(scope)

        // Async variant of the launch-result ITEM_ALREADY_OWNED case: Play told us mid-flow that the
        // user already owns it. Reconcile silently — Play shows its own UI for purchase-sheet
        // failures, so no app-side dialog here.
        billingManager.purchaseFailures
            .filter { it.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED }
            .onEach {
                log(TAG, INFO) { "Async already-owned event -> restoring purchase" }
                try {
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT) { restorePurchaseNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Async already-owned restore failed: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "asyncAlreadyOwned" }
            .launchIn(scope)
    }

    override val upgradeInfo: Flow<Info> = billingManager.billingData
        .map<BillingData, BillingData?> { it }
        .onStart { emit(null) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { data: BillingData? -> data.toUpgradeInfo() }
        .distinctUntilChanged()
        .catch {
            // Ignore Google Play errors if the last pro state was recent
            val now = Clock.System.now().toEpochMilliseconds()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "Catch: now=$now, lastProStateAt=$lastProStateAt, error=$it" }
            if ((now - lastProStateAt).milliseconds < graceWindow()) {
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just and an error, what is GPlay doing???" }
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

    fun launchBillingFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        scope.launch {
            try {
                billingManager.startIapFlow(activity, sku, offer)
            } catch (e: UserCanceledBillingException) {
                log(TAG) { "User canceled the billing flow." }
            } catch (e: Exception) {
                log(TAG) { "startIapFlow failed:${e.asLog()}" }
                withContext(dispatcherProvider.Main) {
                    e.asErrorDialogBuilder(activity).show()
                }
            }
        }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingManager.querySkus(*skus)

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        try {
            val fresh = withTimeout(REFRESH_TIMEOUT) { billingManager.refresh() }
            recordProState(fresh)
        } catch (e: TimeoutCancellationException) {
            log(TAG, ERROR) { "Background refresh timed out" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background refresh: swallow-and-log so callers like the widget config screens aren't
            // affected. The explicit restore path uses restorePurchaseNow(), which surfaces errors.
            log(TAG, ERROR) { "Background refresh failed: ${e.asLog()}" }
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
            fresh.toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's catch: a transient Play error while we were pro recently
            // keeps us pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            val now = Clock.System.now().toEpochMilliseconds()
            val lastProStateAt = billingCache.lastProStateAt.value()
            if ((now - lastProStateAt).milliseconds < graceWindow()) {
                log(TAG, VERBOSE) { "Restore hit a Play error but we were pro recently -> grace" }
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
        val now = Clock.System.now().toEpochMilliseconds()
        val lastProStateAt = billingCache.lastProStateAt.value()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        val info = Info(billingData = this)
        return when {
            preferredProSku(info.upgrades) != null -> info

            (now - lastProStateAt).milliseconds < graceWindow() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> info
        }
    }

    // Persists "we saw a known pro purchase" for the grace machinery. Callers must only pass FRESH
    // data (returned query results, or new emissions seen by the init collector) — never replayed
    // flow data, so a refunded purchase can't keep re-stamping its grace window. Only a *known* pro
    // SKU counts; the permanent IAP is preferred so it drives the window length.
    private suspend fun recordProState(data: BillingData) {
        // A failed product-type query keeps previously cached purchases in billingData (ACK must not
        // starve), but those aren't fresh ownership proof — only purchases whose product type was
        // verified by this data may renew grace. Query-returned data is inherently verified; this
        // only bites for the combined reactive emissions.
        val verified = Info(billingData = data).upgrades.filter {
            when (it.sku.type) {
                Sku.Type.IAP -> data.iapQueryOk
                Sku.Type.SUBSCRIPTION -> data.subQueryOk
            }
        }
        val preferred = preferredProSku(verified) ?: return
        // SKU before timestamp: the timestamp gates grace, the SKU only modifies its length — this
        // order can't leave a fresh gate pointing at a stale modifier if we die between the writes.
        updateLastProSku(preferred, iapQueryOk = data.iapQueryOk)
        billingCache.lastProStateAt.value(Clock.System.now().toEpochMilliseconds())
    }

    // A stored permanent-IAP sku is only replaced by a subscription sku when the IAP query actually
    // succeeded and confirmed no IAP is owned — a transient IAP query failure must not silently
    // shrink the 30d grace window down to the 7d subscription one.
    private suspend fun updateLastProSku(preferred: Sku, iapQueryOk: Boolean) {
        if (preferred.type != Sku.Type.IAP) {
            val storedSku = billingCache.lastProStateSku.value()
            val storedIsIap = OurSku.PRO_SKUS.singleOrNull { it.id == storedSku }?.type == Sku.Type.IAP
            if (storedIsIap && !iapQueryOk) {
                log(TAG) { "Keeping stored IAP sku, the IAP query failed and can't confirm it's gone" }
                return
            }
        }
        billingCache.lastProStateSku.value(preferred.id)
    }

    // Grace window depends on what was last owned: a permanent one-time purchase gets a long window,
    // a subscription (or an unknown/legacy last SKU) gets the short default.
    private suspend fun graceWindow(): Duration {
        val lastSku = billingCache.lastProStateSku.value()
        val type = OurSku.PRO_SKUS.singleOrNull { it.id == lastSku }?.type
        val window = if (type == Sku.Type.IAP) PRO_GRACE_PERIOD_IAP else PRO_GRACE_PERIOD
        log(TAG) { "graceWindow(): lastSku=$lastSku, type=$type -> $window" }
        return window
    }

    data class Info(
        private val gracePeriod: Boolean = false,
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
