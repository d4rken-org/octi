package eu.darken.octi.sync.core

import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference
import eu.darken.octi.sync.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SyncDevicesSortMode(override val label: CaString) : EnumPreference<SyncDevicesSortMode> {
    @SerialName("DATE_ADDED") DATE_ADDED(R.string.sync_devices_sort_date_added_label.toCaString()),
    @SerialName("LAST_SEEN") LAST_SEEN(R.string.sync_devices_sort_last_seen_label.toCaString()),
    @SerialName("NAME") NAME(R.string.sync_devices_sort_name_label.toCaString()),
    @SerialName("APP_VERSION") APP_VERSION(R.string.sync_devices_sort_app_version_label.toCaString()),
    ;
}
