package eu.darken.octi.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import eu.darken.octi.common.upgrade.core.billing.Sku
import eu.darken.octi.common.upgrade.core.billing.SkuDetails
import eu.darken.octi.common.widget.WidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private var initialized = false
    private val widgetsRefreshedForUpgrade = AtomicBoolean(false)

    val events = SingleEventFlow<UpgradeEvents>()
    val billingEvents = SingleEventFlow<BillingEvent>()

    fun initialize(forced: Boolean) {
        if (initialized) return
        initialized = true

        upgradeRepo.upgradeInfo
            .filter { it.isPro }
            .take(1)
            .onEach {
                refreshWidgetsForUpgrade()
                if (!forced) navUp()
            }
            .launchInViewModel()
    }

    private val restoring = MutableStateFlow(false)

    val state: Flow<State> = combine(
        querySkuDetails(OurSku.Iap.PRO_UPGRADE),
        querySkuDetails(OurSku.Sub.PRO_UPGRADE),
        upgradeRepo.upgradeInfo,
        upgradeRepo.wasEverPro,
        restoring,
    ) { iap, sub, current, wasEverPro, isRestoring ->
        // Pricing is deliberately decoupled from entitlement: a returning buyer must see the
        // restore banner and the restore action immediately, even while Google Play is still
        // resolving (or failing to resolve) pricing.
        val pricingLoading = iap is SkuQuery.Loading || sub is SkuQuery.Loading
        val iapDetails = (iap as? SkuQuery.Done)?.details
        val subDetails = (sub as? SkuQuery.Done)?.details
        val pricing = when {
            pricingLoading -> null

            iapDetails == null && subDetails == null -> {
                log(TAG, WARN) { "Pricing unavailable, IAP and SUB queries failed or timed out." }
                null
            }

            else -> Pricing(
                iap = iapDetails?.firstOrNull(),
                sub = subDetails?.firstOrNull(),
                hasIap = current.upgrades.any { it.sku == OurSku.Iap.PRO_UPGRADE },
                hasSub = current.upgrades.any { it.sku == OurSku.Sub.PRO_UPGRADE },
            )
        }
        State(
            pricing = pricing,
            pricingLoading = pricingLoading,
            pricingUnavailable = !pricingLoading && pricing == null,
            // Only advertise the free trial when Play actually returned the trial offer — accounts
            // that already used their trial (or regions without it) get plain subscription copy.
            trialAvailable = pricing?.sub?.details?.subscriptionOfferDetails
                ?.any { OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(it) } == true,
            wasPreviouslyPro = wasEverPro && !current.isPro,
            restoreInProgress = isRestoring,
        )
    }.asStateFlow()

    private sealed interface SkuQuery {
        data object Loading : SkuQuery
        data class Done(val details: Collection<SkuDetails>?) : SkuQuery
    }

    private fun querySkuDetails(sku: Sku): Flow<SkuQuery> = flow {
        emit(SkuQuery.Loading)
        val data = withTimeoutOrNull(SKU_QUERY_TIMEOUT) {
            try {
                upgradeRepo.querySkus(sku)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "SKU query failed for $sku: ${e.asLog()}" }
                null
            }
        }
        emit(SkuQuery.Done(data))
    }

    data class State(
        val pricing: Pricing?,
        val pricingLoading: Boolean = false,
        val pricingUnavailable: Boolean = false,
        val trialAvailable: Boolean = false,
        val wasPreviouslyPro: Boolean = false,
        val restoreInProgress: Boolean = false,
    )

    data class Pricing(
        val iap: SkuDetails?,
        val sub: SkuDetails?,
        val hasSub: Boolean,
        val hasIap: Boolean,
    )

    sealed class BillingEvent {
        data object LaunchIap : BillingEvent()
        data object LaunchSubscription : BillingEvent()
        data object LaunchSubscriptionTrial : BillingEvent()
    }

    fun onGoIap() {
        log(TAG) { "onGoIap()" }
        billingEvents.tryEmit(BillingEvent.LaunchIap)
    }

    fun onGoSubscription() {
        log(TAG) { "onGoSubscription()" }
        billingEvents.tryEmit(BillingEvent.LaunchSubscription)
    }

    fun onGoSubscriptionTrial() {
        log(TAG) { "onGoSubscriptionTrial()" }
        billingEvents.tryEmit(BillingEvent.LaunchSubscriptionTrial)
    }

    fun launchBillingIap(activity: Activity) {
        upgradeRepo.launchBillingFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
    }

    fun launchBillingSubscription(activity: Activity) {
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.BASE_OFFER)
    }

    fun launchBillingSubscriptionTrial(activity: Activity) {
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER)
    }

    fun restorePurchase() {
        // Single-flight: repeated taps while a restore is running (worst case bounded by
        // RESTORE_TIMEOUT) must not stack concurrent restores and duplicate result dialogs.
        if (!restoring.compareAndSet(expect = false, update = true)) {
            log(TAG) { "restorePurchase() ignored, already in progress" }
            return
        }

        launch {
            log(TAG) { "restorePurchase()" }
            try {
                val restored = withTimeoutOrNull(RESTORE_TIMEOUT) { upgradeRepo.restorePurchaseNow() }

                when {
                    restored == null -> {
                        // Play never answered in time; the restore-failed dialog already suggests
                        // waiting / clearing the Play cache, which fits a timeout too.
                        log(TAG, WARN) { "Restore purchase timed out" }
                        events.emit(UpgradeEvents.RestoreFailed)
                    }

                    restored.isPro -> {
                        log(TAG, INFO) { "Restored purchase :))" }
                        refreshWidgetsForUpgrade()
                    }

                    else -> {
                        log(TAG, WARN) { "Restore purchase failed" }
                        events.emit(UpgradeEvents.RestoreFailed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Play/billing error (e.g. service unavailable): surface the proper error dialog
                // instead of the generic "restore failed" message, so the user can tell the two
                // cases apart.
                log(TAG, WARN) { "Restore purchase errored: ${e.asLog()}" }
                errorEvents.emit(e)
            } finally {
                // Reset only after result handling, so the single-flight guard covers the whole
                // user-visible action, including dialog emission.
                restoring.value = false
            }
        }
    }

    private suspend fun refreshWidgetsForUpgrade() {
        if (!widgetsRefreshedForUpgrade.compareAndSet(false, true)) return

        log(TAG) { "refreshWidgetsForUpgrade()" }
        for (manager in widgetManagers) {
            try {
                manager.refreshWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to refresh widgets after upgrade: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val SKU_QUERY_TIMEOUT = 5.seconds
        private val RESTORE_TIMEOUT = 15.seconds
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
