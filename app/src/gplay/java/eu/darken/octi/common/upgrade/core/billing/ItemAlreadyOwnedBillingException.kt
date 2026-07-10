package eu.darken.octi.common.upgrade.core.billing

import eu.darken.octi.R
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError

// Play reports the upgrade as already owned when the buy flow is launched — the stale-state
// returning buyer. Points the user at "Restore purchase" instead of a generic error.
class ItemAlreadyOwnedBillingException(cause: Throwable? = null) :
    BillingException("Purchase is reported as already owned.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_already_owned_error_title.toCaString(),
        description = R.string.upgrades_already_owned_error_description.toCaString(),
    )
}
