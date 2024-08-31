package eu.darken.octi.common.upgrade.ui

sealed class UpgradeEvents {
    data object RestoreFailed : UpgradeEvents()
}
