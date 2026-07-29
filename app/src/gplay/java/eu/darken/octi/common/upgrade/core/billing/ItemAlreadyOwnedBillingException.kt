package eu.darken.octi.common.upgrade.core.billing

import eu.darken.octi.R
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError

class ItemAlreadyOwnedBillingException(cause: Throwable) :
    BillingException("Item is already owned.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_already_owned_error_title.toCaString(),
        description = R.string.upgrades_already_owned_error_description.toCaString(),
    )
}
