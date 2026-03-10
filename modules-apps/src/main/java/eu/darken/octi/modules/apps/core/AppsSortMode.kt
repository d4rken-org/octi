package eu.darken.octi.modules.apps.core

import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference
import eu.darken.octi.modules.apps.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AppsSortMode(override val label: CaString) : EnumPreference<AppsSortMode> {
    @SerialName("NAME") NAME(R.string.module_apps_sort_name_label.toCaString()),
    @SerialName("INSTALLED_AT") INSTALLED_AT(R.string.module_apps_sort_install_date_label.toCaString()),
    @SerialName("UPDATED_AT") UPDATED_AT(R.string.module_apps_sort_update_date_label.toCaString()),
    ;
}
