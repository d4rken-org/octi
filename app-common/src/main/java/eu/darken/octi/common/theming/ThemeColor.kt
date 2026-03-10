package eu.darken.octi.common.theming

import eu.darken.octi.common.R
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeColor(override val label: CaString) : EnumPreference<ThemeColor> {
    @SerialName("GREEN") GREEN(R.string.ui_theme_color_green_label.toCaString()),
    @SerialName("BLUE") BLUE(R.string.ui_theme_color_blue_label.toCaString()),
    @SerialName("SUNSET") SUNSET(R.string.ui_theme_color_sunset_label.toCaString()),
    ;
}
