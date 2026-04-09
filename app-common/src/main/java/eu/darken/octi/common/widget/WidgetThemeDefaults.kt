package eu.darken.octi.common.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import eu.darken.octi.common.R

@Composable
fun widgetDefaultColors(): WidgetTheme.Colors = WidgetTheme.Colors(
    containerBg = colorResource(R.color.widgetContainerBackground).toArgb(),
    onContainer = colorResource(R.color.widgetOnContainer).toArgb(),
    tileBg = colorResource(R.color.widgetTileBackground).toArgb(),
    onTile = colorResource(R.color.widgetOnTile).toArgb(),
    onTileVariant = colorResource(R.color.widgetOnTileVariant).toArgb(),
    accentBg = colorResource(R.color.widgetAccentBackground).toArgb(),
    onAccent = colorResource(R.color.widgetOnAccent).toArgb(),
)
