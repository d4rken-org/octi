package eu.darken.octi.common.theming

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.common.R
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference

@JsonClass(generateAdapter = false)
enum class ThemeColor(override val label: CaString) : EnumPreference<ThemeColor> {
    @Json(name = "GREEN") GREEN(R.string.ui_theme_color_green_label.toCaString()),
    @Json(name = "BLUE") BLUE(R.string.ui_theme_color_blue_label.toCaString()),
    @Json(name = "SUNSET") SUNSET(R.string.ui_theme_color_sunset_label.toCaString()),
    ;
}
