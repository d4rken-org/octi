package eu.darken.octi.modules.apps.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference
import eu.darken.octi.modules.apps.R

@JsonClass(generateAdapter = false)
enum class AppsSortMode(override val label: CaString) : EnumPreference<AppsSortMode> {
    @Json(name = "NAME") NAME(R.string.module_apps_sort_name_label.toCaString()),
    @Json(name = "INSTALLED_AT") INSTALLED_AT(R.string.module_apps_sort_install_date_label.toCaString()),
    @Json(name = "UPDATED_AT") UPDATED_AT(R.string.module_apps_sort_update_date_label.toCaString()),
    ;
}
