package eu.darken.octi.common.theming

import eu.darken.octi.common.R
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeStyle(override val label: CaString) : EnumPreference<ThemeStyle> {
    @SerialName("DEFAULT") DEFAULT(R.string.ui_theme_style_default_label.toCaString()),
    @SerialName("MATERIAL_YOU") MATERIAL_YOU(R.string.ui_theme_style_materialyou_label.toCaString()),
    @SerialName("MEDIUM_CONTRAST") MEDIUM_CONTRAST(R.string.ui_theme_style_medium_contrast_label.toCaString()),
    @SerialName("HIGH_CONTRAST") HIGH_CONTRAST(R.string.ui_theme_style_high_contrast_label.toCaString()),
    ;
}
