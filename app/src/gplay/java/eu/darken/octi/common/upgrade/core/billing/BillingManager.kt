package eu.darken.octi.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Singleton
class BillingManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    connectionProvider: BillingConnectionProvider,
) {

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
            // without a process restart. A device without Play (BILLING_UNAVAILABLE) gets a much
            // longer pause since that state rarely changes.
            val cause = failure
            val retryDelay = when {
                cause is BillingClientException &&
                    cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE -> CONNECTION_RETRY_DELAY_UNAVAILABLE

                else -> CONNECTION_RETRY_DELAY
            }
            log(TAG) { "Retrying billing connection in $retryDelay" }
            delay(retryDelay)
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

    init {
        billingData
            .onEach { data ->
                data.purchases
                    .filter {
                        val needsAck = !it.isAcknowledged

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $it" }
                        else log(TAG) { "Already ACK'ed: $it" }

                        needsAck
                    }
                    .forEach {
                        log(TAG, INFO) { "Acknowledging purchase: $it" }

                        try {
                            useConnection {
                                acknowledgePurchase(it)
                            }
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Failed to ancknowledge purchase: $it\n${e.asLog()}" }
                        }
                    }
            }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) {
                    log(TAG) { "Ack was cancelled (appScope?) cancelled." }
                    return@retryWhen false
                }
                if (attempt > 5) {
                    log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                    return@retryWhen false
                }
                if (cause !is BillingException) {
                    log(TAG, WARN) { "Unknown BillingClient exception type: $cause" }
                    return@retryWhen false
                } else {
                    log(TAG) { "BillingClient exception: $cause" }
                }

                if (cause is BillingClientException && cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                    log(TAG) { "Got BILLING_UNAVAILABLE while trying to ACK purchase." }
                    return@retryWhen false
                }

                log(TAG) { "Will retry ACK" }
                delay((3 * attempt).seconds)
                true
            }
            .launchIn(scope)
    }

    private suspend fun <T> useConnection(action: suspend BillingConnection.() -> T): T = connection.first()
        .getOrElse { throw it.tryMapUserFriendly() }
        .action()

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = useConnection {
        log(TAG) { "querySkus(): $skus..." }
        querySkus(*skus).also {
            log(TAG) { "querySkus(): $it" }
        }
    }

    suspend fun startIapFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        try {
            useConnection {
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
    suspend fun refresh(): BillingData {
        log(TAG) { "refresh()" }
        return useConnection { refreshPurchases() }
    }

    companion object {
        private val CONNECTION_RETRY_DELAY = 1.minutes
        private val CONNECTION_RETRY_DELAY_UNAVAILABLE = 1.hours

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
