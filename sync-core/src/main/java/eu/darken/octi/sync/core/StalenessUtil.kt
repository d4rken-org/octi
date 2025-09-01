package eu.darken.octi.sync.core

import android.content.Context
import eu.darken.octi.sync.R
import java.time.Duration
import java.time.Instant

object StalenessUtil {
    const val STALE_DEVICE_THRESHOLD_DAYS = 30L

    fun isStale(lastSyncTime: Instant?): Boolean {
        if (lastSyncTime == null) return false
        val daysSinceLastSync = Duration.between(lastSyncTime, Instant.now()).toDays()
        return daysSinceLastSync > STALE_DEVICE_THRESHOLD_DAYS
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
        val daysSinceLastSync = Duration.between(lastSyncTime, Instant.now()).toDays().toInt()

        return when {
            daysSinceLastSync < 60 -> {
                context.resources.getQuantityString(
                    R.plurals.sync_stale_period_days,
                    daysSinceLastSync,
                    daysSinceLastSync
                )
            }

            daysSinceLastSync < 365 -> {
                val months = (daysSinceLastSync / 30).toInt()
                context.resources.getQuantityString(
                    R.plurals.sync_stale_period_months,
                    months,
                    months
                )
            }

            else -> {
                val years = (daysSinceLastSync / 365).toInt()
                context.resources.getQuantityString(
                    R.plurals.sync_stale_period_years,
                    years,
                    years
                )
            }
        }
    }
}