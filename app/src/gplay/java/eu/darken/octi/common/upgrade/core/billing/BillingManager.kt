package eu.darken.octi.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.core.billing.client.BillingClientException
import eu.darken.octi.common.upgrade.core.billing.client.BillingConnection
import eu.darken.octi.common.upgrade.core.billing.client.BillingConnectionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Singleton
class BillingManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    connectionProvider: BillingConnectionProvider,
) {

    // Declared before `connection`: the init-block collectors can drive the retry loop on the
    // app scope while this class is still initializing.
    private val retryNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Carries connection failures as values instead of swallowing them: useConnection callers
    // (purchase, restore) get an immediate, mappable error (e.g. BILLING_UNAVAILABLE) instead of
    // hanging on a flow that never emits until some timeout fires. After a failure a fresh
    // connection attempt is made once the retry delay elapses — permanent subscribers (the ACK
    // loop) keep this flow active, so without the loop a single failure would stick in the replay
    // cache and break billing until process restart.
    private val connection: Flow<Result<BillingConnection>> = flow {
        while (true) {
            var failure: Throwable? = null
            connectionProvider.connection
                .onEach {
                    try {
                        it.refreshPurchases()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Initial purchase data refresh failed: ${e.asLog()}" }
                    }
                }
                .map<BillingConnection, Result<BillingConnection>> { Result.success(it) }
                .catch {
                    log(TAG, ERROR) { "Unable to provide client connection:\n${it.asLog()}" }
                    failure = it
                    emit(Result.failure(it))
                }
                .collect { emit(it) }

            // The provider flow only completes after a connection failure — a healthy connection
            // stays open indefinitely. Pause, then attempt a fresh connection so billing recovers
            // without a process restart. A device without Play (BILLING_UNAVAILABLE) gets a longer
            // pause — but not too long: the user may just have signed into their Google account.
            val cause = failure
            val retryDelay = when {
                cause is BillingClientException &&
                    cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE -> CONNECTION_RETRY_DELAY_UNAVAILABLE

                else -> CONNECTION_RETRY_DELAY
            }
            log(TAG) { "Retrying billing connection in $retryDelay (sooner on demand)" }
            // User-facing billing actions can skip the wait via retryNow — e.g. tapping "Restore
            // purchase" right after fixing the Play/account state shouldn't fail on a stale error.
            withTimeoutOrNull(retryDelay) { retryNow.first() }
        }
    }
        .setupCommonEventHandlers(TAG) { "connection" }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeout = 3.seconds, replayExpiration = Duration.ZERO), replay = 1)

    val billingData: Flow<BillingData> = connection
        .mapNotNull { it.getOrNull() }
        .flatMapLatest { it.billingData }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "billingData" }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeout = 3.seconds, replayExpiration = Duration.ZERO), replay = 1)

    // Provenance-tagged conclusive fresh looks (successful queries + push payloads). Grace stamping
    // must use THIS, not the equality-deduped billingData which can mix in stale listener data.
    val freshBillingData: Flow<FreshBillingData> = connection
        .mapNotNull { it.getOrNull() }
        .flatMapLatest { it.freshPurchases }
        .map { FreshBillingData(BillingData(it.purchases), it.isFullSnapshot) }
        .setupCommonEventHandlers(TAG) { "freshBillingData" }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeout = 3.seconds, replayExpiration = Duration.ZERO), replay = 1)

    // Failed attempts at a fresh conclusive look (query errors) — start the unconfirmed-episode
    // clock so a sustained Play outage eventually escalates the grace UI to its diagnostics stage.
    val refreshFailures: Flow<Unit> = connection
        .mapNotNull { it.getOrNull() }
        .flatMapLatest { it.freshFailures }
        .setupCommonEventHandlers(TAG) { "refreshFailures" }

    val purchaseFailures: Flow<BillingResult> = connection
        .mapNotNull { it.getOrNull() }
        .flatMapLatest { it.purchaseFailures }
        .setupCommonEventHandlers(TAG) { "purchaseFailures" }

    // Purchase tokens acknowledged this process. An immutable Purchase snapshot keeps reporting
    // isAcknowledged=false until a fresh query replaces it, so without this guard every billingData
    // re-emission would re-ACK. Recorded only on ACK success. Accessed only from the single ack
    // collector below.
    private val ackedTokens = mutableSetOf<String>()

    // Re-runs the ACK pass independently of distinct billing-state changes: a re-query returning the
    // same still-unacknowledged Purchase is deduped away by billingData's distinctUntilChanged, so a
    // purchase stuck behind a transient Play outage would otherwise never get another ACK attempt
    // before Play's ~3-day auto-refund of unacknowledged purchases.
    private val ackRetryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        combine(billingData, ackRetryTrigger.onStart { emit(Unit) }) { data, _ -> data }
            .onEach { data ->
                var anyFailed = false
                data.purchases
                    .filter { purchase ->
                        val needsAck = !purchase.isAcknowledged && purchase.purchaseToken !in ackedTokens

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $purchase" }
                        else log(TAG) { "Already ACK'ed (or in-flight): $purchase" }

                        needsAck
                    }
                    .forEach { purchase ->
                        log(TAG, INFO) { "Acknowledging purchase: $purchase" }
                        if (acknowledgeWithRetry(purchase)) {
                            ackedTokens.add(purchase.purchaseToken)
                        } else {
                            anyFailed = true
                        }
                    }
                if (anyFailed) {
                    // Schedule a retry that re-reads the latest billingData via the trigger, so a
                    // failed ACK is re-attempted even if the purchase state never changes again.
                    scope.launch {
                        delay(ACK_RESCHEDULE_DELAY)
                        ackRetryTrigger.tryEmit(Unit)
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            .retryWhen { cause, attempt ->
                // acknowledgeWithRetry swallows its own billing errors, so this only fires on an
                // unexpected collector crash; bounded so it can't spin.
                if (cause is CancellationException) {
                    log(TAG) { "Ack collector cancelled (appScope?)." }
                    return@retryWhen false
                }
                if (attempt > 5) {
                    log(TAG, WARN) { "Ack collector reached attempt limit: $attempt due to $cause" }
                    return@retryWhen false
                }
                log(TAG, WARN) { "Ack collector crashed unexpectedly (attempt $attempt): $cause" }
                delay((3 * attempt).seconds)
                true
            }
            .launchIn(scope)
    }

    // Bounded inline ACK retry with linear backoff. Gives up immediately when billing is
    // unavailable (retrying is pointless until reconnect), and after ACK_MAX_ATTEMPTS otherwise.
    // Returns true only on a confirmed acknowledgement. Never throws (except cancellation) — a
    // failed ACK is left for a later emission/reconnect to retry.
    private suspend fun acknowledgeWithRetry(purchase: Purchase): Boolean {
        var attempt = 0
        while (true) {
            attempt++
            try {
                useConnection { acknowledgePurchase(purchase) }
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isBillingUnavailable()) {
                    log(TAG, WARN) { "ACK giving up (billing unavailable) for $purchase" }
                    return false
                }
                if (attempt >= ACK_MAX_ATTEMPTS) {
                    log(TAG, ERROR) { "ACK failed after $attempt attempts for $purchase:\n${e.asLog()}" }
                    return false
                }
                log(TAG, WARN) { "ACK attempt $attempt failed for $purchase, retrying: ${e.asLog()}" }
                delay(ACK_RETRY_BASE * attempt)
            }
        }
    }

    // BILLING_UNAVAILABLE both as the raw client code (acknowledgePurchase throws it unmapped) and
    // as the user-friendly mapped type (a failed connection acquisition inside useConnection).
    private fun Throwable.isBillingUnavailable(): Boolean = when {
        this is BillingClientException && result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE -> true
        this is GplayServiceUnavailableException -> true
        else -> false
    }

    private suspend fun <T> useConnection(
        retryOnFailure: Boolean = false,
        action: suspend BillingConnection.() -> T,
    ): T {
        var current = connection.first()
        if (current.isFailure && retryOnFailure) {
            // A user-facing action must not keep failing on a stale error until the retry delay
            // elapses — the user may just have fixed the cause (signed in, updated Play). Poke the
            // retry loop and wait (bounded — not every caller has its own timeout, e.g. buy taps)
            // for the fresh attempt's outcome. Subscribe before poking so the fresh result can't
            // be missed, and filter on the stale value in case the loop retried on its own in the
            // meantime. If the fresh attempt doesn't resolve in time, fail with the known error.
            log(TAG) { "Connection has a stale failure, requesting an immediate retry" }
            val stale = current
            current = withTimeoutOrNull(RETRY_WAIT_TIMEOUT) {
                coroutineScope {
                    val fresh = async(start = CoroutineStart.UNDISPATCHED) {
                        connection.first { it != stale }
                    }
                    retryNow.tryEmit(Unit)
                    fresh.await()
                }
            } ?: stale
        }
        return current
            .getOrElse { throw it.tryMapUserFriendly() }
            .action()
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = useConnection(retryOnFailure = true) {
        log(TAG) { "querySkus(): $skus..." }
        querySkus(*skus).also {
            log(TAG) { "querySkus(): $it" }
        }
    }

    suspend fun startIapFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        try {
            useConnection(retryOnFailure = true) {
                launchBillingFlow(activity, sku, offer)
            }
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to start IAP flow:\n${e.asLog()}" }
            // Expected environmental/user situations — user-facing handling only, no bug report.
            val ignoredCodes = listOf(
                BillingResponseCode.USER_CANCELED,
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.ERROR,
            )
            when {
                e !is BillingException -> {
                    Bugs.report(RuntimeException("State exception for $sku, U", e))
                }

                e is BillingClientException && !e.result.responseCode.let { ignoredCodes.contains(it) } -> {
                    Bugs.report(RuntimeException("Client exception for $sku", e))
                }
            }

            throw e.tryMapUserFriendly()
        }
    }

    // Query in the caller's context and return the result directly, so callers get the fresh
    // purchases (and any billing error) with a real happens-before instead of racing the shared
    // billingData replay cache.
    suspend fun refresh(): FreshBillingData {
        log(TAG) { "refresh()" }
        val fresh = useConnection(retryOnFailure = true) { refreshPurchases() }
        return FreshBillingData(BillingData(fresh.purchases), fresh.isFullSnapshot)
    }

    // Strict SUBS-only ownership query for the switch-to-IAP gate. Errors propagate so the caller
    // fails closed. Returns the currently-owned subscription purchases (each carries isAutoRenewing).
    suspend fun querySubscriptions(): Collection<Purchase> = useConnection(retryOnFailure = true) {
        log(TAG) { "querySubscriptions()" }
        querySubscriptions()
    }

    companion object {
        private val CONNECTION_RETRY_DELAY = 1.minutes
        internal val CONNECTION_RETRY_DELAY_UNAVAILABLE = 5.minutes
        private val RETRY_WAIT_TIMEOUT = 10.seconds
        private const val ACK_MAX_ATTEMPTS = 3
        private val ACK_RETRY_BASE = 3.seconds
        // Between inline-retry bursts: long enough not to hammer a downed Play, short enough to
        // comfortably beat Play's ~3-day auto-refund of unacknowledged purchases.
        private val ACK_RESCHEDULE_DELAY = 5.minutes

        internal fun Throwable.tryMapUserFriendly(): Throwable {
            if (this !is BillingClientException) return this

            return when (result.responseCode) {
                BillingResponseCode.USER_CANCELED -> UserCanceledBillingException(this)

                BillingResponseCode.ITEM_ALREADY_OWNED -> ItemAlreadyOwnedBillingException(this)

                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.SERVICE_TIMEOUT -> GplayServiceUnavailableException(this)

                else -> this
            }
        }

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Manager")
    }
}
