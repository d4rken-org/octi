package eu.darken.octi.common.widget

import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius

/**
 * Apply rounded corners when running on API 31+, no-op otherwise.
 *
 * Glance's [cornerRadius] requires Android S; calling it on older releases throws at render
 * time. The visual degradation (square corners pre-12) is the accepted tradeoff for a single
 * widget surface that runs across API 28 → current.
 */
fun GlanceModifier.widgetCornerRadius(radius: Dp): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(radius) else this
