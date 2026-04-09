package eu.darken.octi.common.widget

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import eu.darken.octi.common.R

enum class WidgetTheme(
    @StringRes val labelRes: Int,
    val presetBg: Int?,
    val presetAccent: Int?,
) {
    MATERIAL_YOU(R.string.widget_theme_material_you, null, null),
    CLASSIC_GREEN(R.string.widget_theme_classic_green, 0xFF2D6A44.toInt(), 0xFFB1F1C2.toInt()),
    BLUE(R.string.widget_theme_blue, 0xFF1565C0.toInt(), 0xFFBBDEFB.toInt()),
    ORANGE(R.string.widget_theme_orange, 0xFFE65100.toInt(), 0xFFFFCC80.toInt()),
    RED(R.string.widget_theme_red, 0xFFC62828.toInt(), 0xFFFFCDD2.toInt()),
    DARK(R.string.widget_theme_dark, 0xFF1E1E1E.toInt(), 0xFF4CAF50.toInt()),
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

        fun parseHexColor(input: String?): Int? {
            if (input.isNullOrBlank()) return null
            val cleaned = input.trim().removePrefix("#").uppercase()
            if (cleaned.length != 6) return null
            if (!cleaned.matches(Regex("[0-9A-F]{6}"))) return null
            return try {
                (0xFF000000 or cleaned.toLong(16)).toInt()
            } catch (_: NumberFormatException) {
                null
            }
        }

        val SWATCH_COLORS = intArrayOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
            0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
            0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFFFFFFFF.toInt(),
            0xFF1E1E1E.toInt(), 0xFF263238.toInt(), 0xFF1B5E20.toInt(), 0xFF0D47A1.toInt(),
        )
    }
}
