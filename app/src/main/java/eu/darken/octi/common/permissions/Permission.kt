package eu.darken.octi.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import eu.darken.octi.R
import eu.darken.octi.common.BuildConfigWrap

enum class Permission(
    val permissionId: String,
    val isGranted: (Context) -> Boolean = {
        ContextCompat.checkSelfPermission(it, permissionId) == PackageManager.PERMISSION_GRANTED
    },
) {
    ACCESS_COARSE_LOCATION(
        permissionId = "android.permission.ACCESS_COARSE_LOCATION",
    ),
    ACCESS_FINE_LOCATION(
        permissionId = "android.permission.ACCESS_FINE_LOCATION",
    ),
    IGNORE_BATTERY_OPTIMIZATION(
        permissionId = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        isGranted = {
            val pwm = it.getSystemService(Context.POWER_SERVICE) as PowerManager
            pwm.isIgnoringBatteryOptimizations(BuildConfigWrap.APPLICATION_ID)
        },
    ),
    POST_NOTIFICATIONS(
        permissionId = "android.permission.POST_NOTIFICATIONS",
    ),
}


@get:StringRes
val Permission.labelRes: Int
    get() = when (this) {
        Permission.ACCESS_COARSE_LOCATION, Permission.ACCESS_FINE_LOCATION -> R.string.permission_location_label
        Permission.IGNORE_BATTERY_OPTIMIZATION -> R.string.permission_battery_optimization_label
        Permission.POST_NOTIFICATIONS -> R.string.permission_notifications_post_label
    }

@get:StringRes
val Permission.descriptionRes: Int
    get() = when (this) {
        Permission.ACCESS_COARSE_LOCATION, Permission.ACCESS_FINE_LOCATION -> R.string.permission_location_desc
        Permission.IGNORE_BATTERY_OPTIMIZATION -> R.string.permission_battery_optimization_desc
        Permission.POST_NOTIFICATIONS -> R.string.permission_notifications_post_desc
    }