package eu.darken.octi.common.upgrade.ui

sealed class UpgradeEvents {
    data object RestoreFailed : UpgradeEvents()
    data object RestoreSucceeded : UpgradeEvents()
    data object SubscriptionStillRenewing : UpgradeEvents()
    data object SubscriptionCheckFailed : UpgradeEvents()
}
