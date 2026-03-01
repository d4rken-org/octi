package eu.darken.octi.common.theming

import androidx.compose.material3.ColorScheme

object ThemeColorProvider {

    fun getLightColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.GREEN -> OctiColorsGreen.lightScheme(style)
        ThemeColor.BLUE -> OctiColorsBlue.lightScheme(style)
        ThemeColor.SUNSET -> OctiColorsSunset.lightScheme(style)
    }

    fun getDarkColorScheme(color: ThemeColor, style: ThemeStyle): ColorScheme = when (color) {
        ThemeColor.GREEN -> OctiColorsGreen.darkScheme(style)
        ThemeColor.BLUE -> OctiColorsBlue.darkScheme(style)
        ThemeColor.SUNSET -> OctiColorsSunset.darkScheme(style)
    }
}
