package eu.darken.octi.common.upgrade

import android.content.Context

/**
 * Routes an external entry-point (e.g. a widget configure Activity, or a Glance widget click that
 * runs on a background worker) into the existing Octi Pro upgrade flow. Flavour-specific binding
 * decides what the destination screen does (sponsor flow on FOSS, IAP on Gplay).
 *
 * Implementations must tolerate being called from a non-Activity [Context] — they add the right
 * intent flags internally so callers don't need to special-case that.
 */
interface UpgradeLauncher {
    fun launch(context: Context)
}
