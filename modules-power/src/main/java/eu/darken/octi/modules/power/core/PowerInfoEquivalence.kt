package eu.darken.octi.modules.power.core

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Sync-oriented equivalence for [PowerInfo] with per-field tolerances.
 *
 * The battery broadcast (`ACTION_BATTERY_CHANGED`) fires roughly once per second while (dis)charging,
 * and several [PowerInfo] fields jitter on every read: the raw current registers ([ChargeIO.currentNow]
 * / [ChargeIO.currenAvg]), the temperature (0.1 °C steps), and the `now()`-based charge ETA
 * ([ChargeIO.fullAt] / [ChargeIO.fullSince]). Plain data-class equality therefore treats every sample as
 * "changed", defeating the `distinctUntilChanged()` in `BaseModuleRepo` and pushing a module sync every
 * second.
 *
 * Used with [kotlinx.coroutines.flow.distinctUntilChanged], where the comparison is against the last
 * EMITTED value (hysteresis): sub-tolerance drift accumulates against that anchor rather than the
 * previous sample, so a slow ramp still eventually emits, and values that oscillate just under a
 * tolerance boundary don't flap. The emitted value is always the real sample — nothing is rounded — so
 * derived data (e.g. [ChargeIO.speed]) and the wire payload keep full precision; we simply emit less
 * often.
 */
internal fun PowerInfo.isEquivalentForSync(other: PowerInfo): Boolean {
    if (status != other.status) return false
    if (!battery.isEquivalentForSync(other.battery)) return false
    if (!chargeIO.isEquivalentForSync(other.chargeIO)) return false
    return true
}

private fun PowerInfo.Battery.isEquivalentForSync(other: PowerInfo.Battery): Boolean {
    if (level != other.level) return false
    if (scale != other.scale) return false
    if (health != other.health) return false
    if (!floatsEquivalent(temp, other.temp, TEMP_TOLERANCE_C)) return false
    return true
}

private fun PowerInfo.ChargeIO.isEquivalentForSync(other: PowerInfo.ChargeIO): Boolean {
    // The current tolerance must not swallow a change that flips the semantic charge-speed category
    // (e.g. 980 mA → 1020 mA is <50 mA apart but crosses SLOW→NORMAL), or the UI would show a stale
    // "charging slowly/normally/fast" indefinitely.
    if (speed != other.speed) return false
    if (!intsEquivalent(currentNow, other.currentNow, CURRENT_TOLERANCE_UA)) return false
    if (!intsEquivalent(currenAvg, other.currenAvg, CURRENT_TOLERANCE_UA)) return false
    if (!instantsEquivalent(fullSince, other.fullSince, ETA_TOLERANCE)) return false
    if (!instantsEquivalent(fullAt, other.fullAt, ETA_TOLERANCE)) return false
    if (!instantsEquivalent(emptyAt, other.emptyAt, ETA_TOLERANCE)) return false
    return true
}

// A null↔non-null transition is always significant; two non-null values within tolerance are equivalent.
private fun intsEquivalent(a: Int?, b: Int?, toleranceUa: Int): Boolean {
    if (a == null || b == null) return a == b
    return abs(a.toLong() - b.toLong()) < toleranceUa
}

private fun floatsEquivalent(a: Float?, b: Float?, tolerance: Float): Boolean {
    if (a == null || b == null) return a == b
    return abs(a - b) < tolerance
}

private fun instantsEquivalent(a: Instant?, b: Instant?, tolerance: Duration): Boolean {
    if (a == null || b == null) return a == b
    return (a - b).absoluteValue < tolerance
}

// Below these, a delta is treated as noise and does not trigger a sync.
internal const val CURRENT_TOLERANCE_UA = 50_000 // 50 mA
internal const val TEMP_TOLERANCE_C = 1.0f // 1 °C
internal val ETA_TOLERANCE = 1.minutes
