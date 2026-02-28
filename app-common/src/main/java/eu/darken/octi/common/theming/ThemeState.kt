package eu.darken.octi.common.theming

data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.DEFAULT,
)
