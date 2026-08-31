package eu.darken.octi.common.widget

import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The de-emphasized roles are what stale widget rows render in. They are derived by blending
 * towards their own background, so a bad blend ratio produces text that is technically drawn
 * but unreadable. This pins a floor over every colour combination a user can actually pick.
 *
 * 2.5 is measured, not aspirational: the worst swatch pair sits at 2.64 (onTileVariant) and
 * 3.14 (onContainerVariant), the worst preset at 2.79 (ORANGE) and 2.96 (RED).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WidgetThemeContrastTest {

    private val minContrast = 2.5

    private fun assertVariantContrast(bg: Int, accent: Int, label: String) {
        val colors = WidgetTheme.deriveColors(bg, accent)

        val tileContrast = ColorUtils.calculateContrast(colors.onTileVariant, colors.tileBg)
        assertTrue(
            "$label: onTileVariant vs tileBg contrast $tileContrast < $minContrast " +
                "(bg=#${Integer.toHexString(bg)}, accent=#${Integer.toHexString(accent)})",
            tileContrast >= minContrast,
        )

        val containerContrast = ColorUtils.calculateContrast(colors.onContainerVariant, colors.containerBg)
        assertTrue(
            "$label: onContainerVariant vs containerBg contrast $containerContrast < $minContrast " +
                "(bg=#${Integer.toHexString(bg)}, accent=#${Integer.toHexString(accent)})",
            containerContrast >= minContrast,
        )
    }

    @Test
    fun `every preset keeps the de-emphasized roles readable`() {
        WidgetTheme.entries
            .filter { it.presetBg != null && it.presetAccent != null }
            .forEach { theme ->
                assertVariantContrast(theme.presetBg!!, theme.presetAccent!!, theme.name)
            }
    }

    @Test
    fun `every swatch combination keeps the de-emphasized roles readable`() {
        WidgetTheme.SWATCH_COLORS.forEach { bg ->
            WidgetTheme.SWATCH_COLORS.forEach { accent ->
                assertVariantContrast(bg, accent, "swatch")
            }
        }
    }
}
