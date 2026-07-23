package eu.darken.octi.common.upgrade.core.billing.client

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.upgrade.core.billing.BillingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class BillingConnectionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val provider: Flow<BillingConnection> = callbackFlow {
        // Reconcilable listener overlay: onPurchasesUpdated writes here, fresh queries prune it.
        val purchasesGlobal = MutableStateFlow<Collection<Purchase>>(emptyList())
        val purchaseFailureEvents = MutableStateFlow<BillingResult?>(null)
        // replay=1: a fresh observation can arrive before UpgradeRepoGplay's grace collector
        // subscribes (construction-order race) — the latest fresh look must not be lost.
        val freshObservations = MutableSharedFlow<FreshPurchases>(
            replay = 1,
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        // replay=1: the initial refresh can fail before the failure collector subscribes — that
        // first failure must still be able to start the unconfirmed-episode clock. A stale replayed
        // failure is harmless (episode recording is set-if-unset and guarded).
        val freshFailures = MutableSharedFlow<Unit>(
            replay = 1,
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        // Bumped on every successful onPurchasesUpdated BEFORE its data is published: a refresh
        // compares the generation around its queries to detect a racing purchase event, which
        // downgrades that refresh's snapshot from "proves absence" to "presence only".
        val listenerGeneration = AtomicLong(0)

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder().apply {
            enableOneTimeProducts()
            enablePrepaidPlans()
        }.build()

        val client = BillingClient.newBuilder(context).apply {
            enablePendingPurchases(pendingPurchasesParams)
            setListener { result, purchases ->
                if (result.isSuccess) {
                    log(TAG) {
                        "onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                    listenerGeneration.incrementAndGet()
                    purchasesGlobal.value = purchases.orEmpty()
                    freshObservations.tryEmit(
                        FreshPurchases(
                            purchases = purchases.orEmpty().filter { it.purchaseState == Purchase.PurchaseState.PURCHASED },
                            // Push payloads only carry this session's purchases — they prove
                            // presence, never absence.
                            isFullSnapshot = false,
                        )
                    )
                } else {
                    log(TAG, WARN) {
                        "error: onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                    // Failures go to their own slot: async ITEM_ALREADY_OWNED drives the auto-restore
                    // in UpgradeRepoGplay. They must NOT overwrite the success slot — a purchase event
                    // not yet reflected in the query caches would vanish from billingData (and could
                    // starve acknowledgement) if a later cancel/error replaced it.
                    purchaseFailureEvents.value = result
                }
            }
        }.build()

        log(TAG, VERBOSE) { "startConnection(...)" }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                log(TAG, VERBOSE) {
                    "onBillingSetupFinished(code=${result.responseCode}, message=${result.debugMessage})"
                }

                when (result.responseCode) {
                    BillingResponseCode.OK -> {
                        val connection = BillingConnection(
                            client = client,
                            purchasesGlobal = purchasesGlobal,
                            freshObservations = freshObservations,
                            freshFailuresGlobal = freshFailures,
                            purchaseFailureEvents = purchaseFailureEvents,
                            listenerGeneration = { listenerGeneration.get() },
                        )
                        trySendBlocking(connection)
                    }

                    else -> {
                        close(BillingClientException(result))
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                log(TAG) { "onBillingServiceDisconnected() " }
                close(BillingException("Billing service disconnected"))
            }
        })

        log(TAG) { "Awaiting close." }
        awaitClose {
            try {
                log(TAG) { "Stopping billing client connection" }
                client.endConnection()
            } catch (e: Exception) {
                log(TAG, WARN) { "Couldn't end billing client connection: ${e.asLog()}" }
            }
        }
    }

    val connection: Flow<BillingConnection> = provider
        .setupCommonEventHandlers(TAG) { "provider" }
        .retryWhen { cause, attempt ->
            log(TAG) { "Billing client connection error: ${cause.asLog()}" }

            if (cause is CancellationException) {
                log(TAG) { "BillingClient connection cancelled." }
                return@retryWhen false
            }

            if (cause !is BillingException) {
                log(TAG, WARN) { "Unknown exception type: $cause" }
                return@retryWhen false
            }

            if (cause is BillingClientException && cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                log(TAG) { "Got BILLING_UNAVAILABLE while trying to connect client." }
                return@retryWhen false
            }

            if (attempt > 5) {
                log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                return@retryWhen false
            }

            log(TAG) { "Will retry BillingClient connection... *sigh*" }
            delay((2 * attempt).seconds)
            true
        }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Client", "ConnectionProvider")
    }
}