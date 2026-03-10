package eu.darken.octi.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import eu.darken.octi.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.octi.common.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private var initialized = false

    val events = SingleEventFlow<UpgradeEvents>()
    val billingEvents = SingleEventFlow<BillingEvent>()

    fun initialize(forced: Boolean) {
        if (initialized) return
        initialized = true

        if (!forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { navUp() }
                .launchInViewModel()
        }
    }

    val state: Flow<Pricing> = combine(
        flow {
            val data = withTimeoutOrNull(5000) {
                try {
                    upgradeRepo.querySkus(OurSku.Iap.PRO_UPGRADE)
                } catch (e: Exception) {
                    errorEvents.emitBlocking(e)
                    null
                }
            }
            emit(data)
        },
        flow {
            val data = withTimeoutOrNull(5000) {
                try {
                    upgradeRepo.querySkus(OurSku.Sub.PRO_UPGRADE)
                } catch (e: Exception) {
                    errorEvents.emitBlocking(e)
                    null
                }
            }
            emit(data)
        },
        upgradeRepo.upgradeInfo,
    ) { iap, sub, current ->
        if (iap == null && sub == null) {
            throw GplayServiceUnavailableException(RuntimeException("IAP and SUB data request timed out."))
        }
        Pricing(
            iap = iap?.first(),
            sub = sub?.first(),
            hasIap = current.upgrades.any { it.sku == OurSku.Iap.PRO_UPGRADE },
            hasSub = current.upgrades.any { it.sku == OurSku.Sub.PRO_UPGRADE },
        )
    }

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

    fun restorePurchase() = launch {
        log(TAG) { "restorePurchase()" }

        log(TAG, VERBOSE) { "Refreshing" }
        upgradeRepo.refresh()

        val refreshedState = upgradeRepo.upgradeInfo.first()
        log(TAG) { "Refreshed purchase state: $refreshedState" }

        if (refreshedState.isPro) {
            log(TAG, INFO) { "Restored purchase :))" }
        } else {
            log(TAG, WARN) { "Restore purchase failed" }
            events.emit(UpgradeEvents.RestoreFailed)
        }
    }

    companion object {
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
