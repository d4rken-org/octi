package eu.darken.octi.common.upgrade.core.billing

open class BillingException(
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception()