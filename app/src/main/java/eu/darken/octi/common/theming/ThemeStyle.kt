package eu.darken.octi.common.theming

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.octi.R
import eu.darken.octi.common.ca.CaString
import eu.darken.octi.common.ca.toCaString
import eu.darken.octi.common.preferences.EnumPreference

@JsonClass(generateAdapter = false)
enum class ThemeStyle(override val label: CaString) : EnumPreference<ThemeStyle> {
    @Json(name = "DEFAULT") DEFAULT(R.string.ui_theme_style_default_label.toCaString()),
    @Json(name = "MATERIAL_YOU") MATERIAL_YOU(R.string.ui_theme_style_materialyou_label.toCaString()),
    ;
}