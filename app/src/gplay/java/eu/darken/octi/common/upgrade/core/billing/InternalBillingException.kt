package eu.darken.octi.common.upgrade.core.billing

import eu.darken.octi.R
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError

class InternalBillingException(cause: Throwable) :
    BillingException("An internal Google Play error occurred.", cause), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_internal_error_title.toCaString(),
        description = R.string.upgrades_gplay_internal_error_description.toCaString(),
    )
}
