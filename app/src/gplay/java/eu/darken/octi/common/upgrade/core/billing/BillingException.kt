package eu.darken.octi.common.upgrade.core.billing

import eu.darken.octi.R
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.error.HasLocalizedError
import eu.darken.octi.common.error.LocalizedError

open class BillingException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(), HasLocalizedError {

    override fun getLocalizedError(): LocalizedError = LocalizedError(
        throwable = this,
        label = R.string.upgrades_gplay_billing_error_label.toCaString(),
        description = R.string.upgrades_gplay_billing_error_description.toCaString(message ?: "")
    )
}
