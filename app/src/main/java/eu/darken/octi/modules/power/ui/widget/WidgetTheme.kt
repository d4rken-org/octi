package eu.darken.octi.modules.power.ui.widget

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import eu.darken.octi.modules.power.R as PowerR

enum class WidgetTheme(
    @StringRes val labelRes: Int,
    val presetBg: Int?,
    val presetAccent: Int?,
) {
    MATERIAL_YOU(PowerR.string.module_power_widget_theme_material_you, null, null),
    CLASSIC_GREEN(PowerR.string.module_power_widget_theme_classic_green, 0xFF2D6A44.toInt(), 0xFFB1F1C2.toInt()),
    BLUE(PowerR.string.module_power_widget_theme_blue, 0xFF1565C0.toInt(), 0xFFBBDEFB.toInt()),
    ORANGE(PowerR.string.module_power_widget_theme_orange, 0xFFE65100.toInt(), 0xFFFFCC80.toInt()),
    RED(PowerR.string.module_power_widget_theme_red, 0xFFC62828.toInt(), 0xFFFFCDD2.toInt()),
    DARK(PowerR.string.module_power_widget_theme_dark, 0xFF1E1E1E.toInt(), 0xFF4CAF50.toInt()),
    ;

    data class Colors(
        val containerBg: Int,
        val barFill: Int,
        val barTrack: Int,
        val icon: Int,
        val onContainer: Int,
    )

    companion object {
        internal fun bestContrast(bg: Int): Int {
            val contrastWhite = ColorUtils.calculateContrast(Color.WHITE, bg)
            val contrastBlack = ColorUtils.calculateContrast(Color.BLACK, bg)
            return if (contrastWhite >= contrastBlack) Color.WHITE else Color.BLACK
        }

        fun deriveColors(bg: Int, accent: Int): Colors = Colors(
            containerBg = bg,
            barFill = accent,
            barTrack = ColorUtils.setAlphaComponent(accent, 0x55),
            icon = bestContrast(accent),
            onContainer = bestContrast(bg),
        )

        fun fromName(name: String?): WidgetTheme? = entries.find { it.name == name }

        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_CUSTOM_BG = "custom_bg"
        const val KEY_CUSTOM_ACCENT = "custom_accent"

        const val MODE_MATERIAL_YOU = "material_you"
        const val MODE_CUSTOM = "custom"
    }
}
