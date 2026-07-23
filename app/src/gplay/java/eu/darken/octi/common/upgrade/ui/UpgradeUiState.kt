package eu.darken.octi.common.upgrade.ui

import eu.darken.octi.common.upgrade.core.OurSku
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import eu.darken.octi.common.upgrade.core.billing.SkuDetails

sealed interface UpgradeUiState {

    data object Loading : UpgradeUiState

    data class Loaded(
        val subscriptionAction: SubscriptionAction,
        val subscriptionEnabled: Boolean,
        val subscriptionPrice: String?,
        val iapEnabled: Boolean,
        val iapPrice: String?,
        val ownership: Ownership = Ownership(),
        val grace: GraceHint? = null,
        val showRestoreBanner: Boolean = false,
        val settled: Boolean = true,
        val restoreInProgress: Boolean = false,
        val verificationInProgress: Boolean = false,
        // Manage route + free user: show the calm status page first, revealing the offers only when
        // the user asks. Sales route or an existing owner/grace user always sees the relevant view.
        val manageMode: Boolean = false,
        val viewingOffers: Boolean = false,
    ) : UpgradeUiState {
        val subAvailable: Boolean get() = subscriptionAction != SubscriptionAction.UNAVAILABLE
        val iapAvailable: Boolean get() = iapPrice != null
        val isFree: Boolean get() = !ownership.ownsAnything && grace == null
        // Free user on the manage route who hasn't asked to see the offers yet.
        val showFreeStatus: Boolean get() = isFree && manageMode && !viewingOffers
    }
}

// Pro but no owned purchase in the current data — the grace period is carrying the entitlement.
// Quiet at first (a Play hiccup usually resolves itself), diagnostics once the unconfirmed episode
// has aged past the threshold.
data class GraceHint(val showDiagnostics: Boolean)

data class Ownership(
    val hasIap: Boolean = false,
    val subscription: SubscriptionOwnership? = null,
) {
    val ownsAnything: Boolean get() = hasIap || subscription != null
}

data class SubscriptionOwnership(val isAutoRenewing: Boolean)

enum class SubscriptionAction { TRIAL, STANDARD, UNAVAILABLE }

// Conservative: if ANY record for the sub SKU still claims auto-renew, treat it as renewing — that
// can only under-offer the switch to the one-time purchase, and the purchase gate re-verifies
// against a fresh SUBS query before any billing flow starts anyway.
fun UpgradeRepoGplay.Info.toOwnership() = Ownership(
    hasIap = upgrades.any { it.sku.id == OurSku.Iap.PRO_UPGRADE.id },
    subscription = upgrades
        .filter { it.sku.id == OurSku.Sub.PRO_UPGRADE.id }
        .takeIf { it.isNotEmpty() }
        ?.let { subs -> SubscriptionOwnership(isAutoRenewing = subs.any { it.purchase.isAutoRenewing }) },
)

// Aggregate result of the one-shot SKU detail queries. `done` distinguishes "queries still running"
// from "queries finished but found nothing" — owners render without waiting either way.
data class SkuQueryState(
    val done: Boolean = false,
    val iap: SkuDetails? = null,
    val sub: SkuDetails? = null,
)

fun toLoadedState(
    skus: SkuQueryState,
    ownership: Ownership,
    grace: GraceHint?,
    showRestoreBanner: Boolean,
    settled: Boolean,
    restoreInProgress: Boolean,
    verificationInProgress: Boolean,
    manageMode: Boolean,
    viewingOffers: Boolean,
): UpgradeUiState.Loaded {
    val iapOffer = skus.iap?.details?.oneTimePurchaseOfferDetails
    val subOffers = skus.sub?.details?.subscriptionOfferDetails
    val baseOffer = subOffers?.firstOrNull { OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(it) }
    val trialOffer = subOffers?.firstOrNull { OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(it) }

    return UpgradeUiState.Loaded(
        subscriptionAction = when {
            trialOffer != null -> SubscriptionAction.TRIAL
            baseOffer != null -> SubscriptionAction.STANDARD
            else -> SubscriptionAction.UNAVAILABLE
        },
        // `settled` gates all purchase actions until the first billing reconciliation (or its
        // bounded fallback): the initially-empty purchase state must not let an owner on a fresh
        // install buy the other product before their existing purchase has been seen.
        subscriptionEnabled = settled && ownership.subscription == null && !restoreInProgress && !verificationInProgress,
        subscriptionPrice = baseOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
        iapEnabled = settled && !ownership.hasIap && !restoreInProgress && !verificationInProgress,
        iapPrice = iapOffer?.formattedPrice,
        ownership = ownership,
        grace = grace,
        showRestoreBanner = showRestoreBanner,
        settled = settled,
        restoreInProgress = restoreInProgress,
        verificationInProgress = verificationInProgress,
        manageMode = manageMode,
        viewingOffers = viewingOffers,
    )
}
