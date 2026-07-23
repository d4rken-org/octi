package eu.darken.octi.common.upgrade.core.billing.client

import com.android.billingclient.api.Purchase

// Provenance-tagged fresh purchase observation: a full snapshot (both product types queried
// conclusively, with no purchase event racing the queries) proves absence; anything else — push
// payloads, single-type queries, partial or raced refreshes — proves presence only.
data class FreshPurchases(
    val purchases: Collection<Purchase>,
    val isFullSnapshot: Boolean,
)
