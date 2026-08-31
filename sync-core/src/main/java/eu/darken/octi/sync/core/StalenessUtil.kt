package eu.darken.octi.sync.core

import android.content.Context
import eu.darken.octi.sync.R
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object StalenessUtil {
    const val STALE_DEVICE_THRESHOLD_DAYS = 7L

    fun isStale(lastSyncTime: Instant?): Boolean = isStale(lastSyncTime, Clock.System.now())

    /** A device is stale once the age of its newest data reaches [STALE_DEVICE_THRESHOLD_DAYS]. */
    internal fun isStale(lastSyncTime: Instant?, now: Instant): Boolean {
        if (lastSyncTime == null) return false
        return (now - lastSyncTime) >= STALE_DEVICE_THRESHOLD_DAYS.days
    }

    fun SyncRead.Device.isStale(): Boolean {
        val mostRecentSync = modules.maxOfOrNull { it.modifiedAt }
        return isStale(mostRecentSync)
    }

    fun SyncRead?.countStaleDevices(): Int {
        if (this?.devices == null) return 0
        return devices.count { it.isStale() }
    }

    fun formatStalePeriod(context: Context, lastSyncTime: Instant): String {
        val daysSinceLastSync = (Clock.System.now() - lastSyncTime).inWholeDays.toInt()

        return when {
            daysSinceLastSync < 60 -> {
                context.resources.getQuantityString(
                    R.plurals.sync_stale_period_days,
                    daysSinceLastSync,
                    daysSinceLastSync
                )
            }

            daysSinceLastSync < 365 -> {
                val months = daysSinceLastSync / 30
                context.resources.getQuantityString(
                    R.plurals.sync_stale_period_months,
                    months,
                    months
                )
            }

            else -> {
                val years = daysSinceLastSync / 365
                context.resources.getQuantityString(
                    R.plurals.sync_stale_period_years,
                    years,
                    years
                )
            }
        }
    }
}