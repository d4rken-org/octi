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
        // Offer-box phase, independent of the dashboard: an owner/grace/free user renders their own
        // view even while (or after) the SKU queries are still loading or have failed.
        val offers: OfferState = OfferState.Loading,
        val ownership: Ownership = Ownership(),
        val grace: GraceHint? = null,
        val showRestoreBanner: Boolean = false,
        val settled: Boolean = true,
        // Manual restore only — drives the in-button spinner.
        val restoreInProgress: Boolean = false,
        // Manual OR the repo's invisible already-owned auto-restore — disables restore surfaces so a
        // manual restore can't race the automatic one (which the VM's CAS guard can't see).
        val restoreBusy: Boolean = false,
        // Pre-IAP verification / purchase launch OR auto-restore — disables purchase actions.
        val verificationInProgress: Boolean = false,
        // Manage route + free user: show the calm status page first, revealing the offers only when
        // the user asks. Sales route or an existing owner/grace user always sees the relevant view.
        val manageMode: Boolean = false,
        val viewingOffers: Boolean = false,
    ) : UpgradeUiState {
        val subAvailable: Boolean get() = subscriptionAction != SubscriptionAction.UNAVAILABLE
        val iapAvailable: Boolean get() = iapPrice != null
        // Any entitlement operation (manual/auto restore, purchase verify/launch) is in flight — the
        // single-flight guard rejects a second one, so every action surface disables to match.
        val anyOperationInProgress: Boolean get() = restoreBusy || verificationInProgress
        val isFree: Boolean get() = !ownership.ownsAnything && grace == null
        // Free user on the manage route who hasn't asked to see the offers yet.
        val showFreeStatus: Boolean get() = isFree && manageMode && !viewingOffers
    }
}

// The acquisition offers box phase. Prices/actions themselves live on Loaded (owners read the IAP
// price for the switch offer regardless of this phase); this only gates the offers-box rendering.
sealed interface OfferState {
    data object Loading : OfferState
    data class Unavailable(val error: Throwable) : OfferState
    data object Ready : OfferState
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

// One aggregate SKU-detail query per retry generation. `Pending` distinguishes "queries still
// running" from a finished `Done` — a Done whose Results are both failures becomes the offers-box
// Unavailable state, while both empty-but-successful stays Ready (a product simply has no offer).
sealed interface SkuQueries {
    data object Pending : SkuQueries
    data class Done(
        val iap: Result<List<SkuDetails>>,
        val sub: Result<List<SkuDetails>>,
    ) : SkuQueries
}

fun toLoadedState(
    queries: SkuQueries,
    ownership: Ownership,
    grace: GraceHint?,
    showRestoreBanner: Boolean,
    settled: Boolean,
    restoreInProgress: Boolean,
    restoreBusy: Boolean,
    verificationInProgress: Boolean,
    manageMode: Boolean,
    viewingOffers: Boolean,
): UpgradeUiState.Loaded {
    val done = queries as? SkuQueries.Done
    val iapDetails = done?.iap?.getOrNull()?.firstOrNull()
    val subDetails = done?.sub?.getOrNull()?.firstOrNull()

    val iapOffer = iapDetails?.details?.oneTimePurchaseOfferDetails
    val subOffers = subDetails?.details?.subscriptionOfferDetails
    val baseOffer = subOffers?.firstOrNull { OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(it) }
    val trialOffer = subOffers?.firstOrNull { OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(it) }

    val subscriptionAction = when {
        trialOffer != null -> SubscriptionAction.TRIAL
        baseOffer != null -> SubscriptionAction.STANDARD
        else -> SubscriptionAction.UNAVAILABLE
    }
    val subscriptionPrice = baseOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    val iapPrice = iapOffer?.formattedPrice
    val subAvailable = subscriptionAction != SubscriptionAction.UNAVAILABLE
    val iapAvailable = iapPrice != null

    val offers: OfferState = when {
        done == null -> OfferState.Loading
        // Only a genuine query FAILURE for BOTH products is "unavailable". Empty-but-successful
        // stays Ready — a product just has no offer, and its button ends up disabled below.
        done.iap.isFailure && done.sub.isFailure -> OfferState.Unavailable(
            done.iap.exceptionOrNull() ?: done.sub.exceptionOrNull() ?: IllegalStateException("Offers unavailable"),
        )

        else -> OfferState.Ready
    }

    // `settled` gates all purchase actions until the first billing reconciliation (or its bounded
    // fallback): an owner on a fresh install must not buy the other product before their existing
    // purchase has been seen. `!available` keeps a product whose offer didn't load un-launchable.
    val busy = restoreBusy || verificationInProgress
    return UpgradeUiState.Loaded(
        subscriptionAction = subscriptionAction,
        subscriptionEnabled = settled && ownership.subscription == null && subAvailable && !busy,
        subscriptionPrice = subscriptionPrice,
        iapEnabled = settled && !ownership.hasIap && iapAvailable && !busy,
        iapPrice = iapPrice,
        offers = offers,
        ownership = ownership,
        grace = grace,
        showRestoreBanner = showRestoreBanner,
        settled = settled,
        restoreInProgress = restoreInProgress,
        restoreBusy = restoreBusy,
        verificationInProgress = verificationInProgress,
        manageMode = manageMode,
        viewingOffers = viewingOffers,
    )
}
