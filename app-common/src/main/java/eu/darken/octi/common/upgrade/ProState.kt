package eu.darken.octi.common.upgrade

/**
 * Resolved Octi Pro entitlement state for UI gating.
 *
 * Boolean isPro is too lossy for callers that distinguish "not yet checked" from "checked and
 * not Pro" — notably Gplay's [UpgradeRepoGplay] seeds an initial null-mapped emission that resolves
 * as isPro=false before the billing client has loaded. Treat that initial emission as [Checking]
 * to avoid false-locking a paying user during cold start.
 */
sealed interface ProState {
    data object Checking : ProState
    data object Unlocked : ProState
    data object Locked : ProState
    data class Error(val cause: Throwable) : ProState
}
