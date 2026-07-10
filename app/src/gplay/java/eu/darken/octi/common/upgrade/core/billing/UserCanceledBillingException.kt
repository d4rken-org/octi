package eu.darken.octi.common.upgrade.core.billing

// The user backed out of the billing flow — expected behavior, callers swallow this silently.
class UserCanceledBillingException(cause: Throwable? = null) :
    BillingException("User canceled the billing flow.", cause)
