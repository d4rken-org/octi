package eu.darken.octi.common.theming

import eu.darken.octi.common.R
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(override val label: CaString) : EnumPreference<ThemeMode> {
    @SerialName("SYSTEM") SYSTEM(R.string.ui_theme_mode_system_label.toCaString()),
    @SerialName("DARK") DARK(R.string.ui_theme_mode_dark_label.toCaString()),
    @SerialName("LIGHT") LIGHT(R.string.ui_theme_mode_light_label.toCaString()),
    ;
}
