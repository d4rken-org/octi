package eu.darken.octi.common.permissions

import androidx.annotation.StringRes
import eu.darken.octi.R

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