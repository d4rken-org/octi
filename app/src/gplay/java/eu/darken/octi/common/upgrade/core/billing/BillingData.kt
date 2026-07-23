package eu.darken.octi.common.upgrade.core.billing

import com.android.billingclient.api.Purchase

data class BillingData(
    val purchases: Collection<Purchase>,
    // Per-product-type query status: false means the last INAPP/SUBS query failed and `purchases`
    // may be missing entries of that type. Lets consumers distinguish "confirmed not owned" from
    // "couldn't verify this product type".
    val iapQueryOk: Boolean = true,
    val subQueryOk: Boolean = true,
)

// Provenance-tagged fresh billing data: only a full snapshot (both product types queried
// conclusively, no racing purchase event) proves absence — anything else proves presence only.
// The grace machinery relies on this to never start an unconfirmed episode from partial data.
data class FreshBillingData(
    val data: BillingData,
    val isFullSnapshot: Boolean,
)
