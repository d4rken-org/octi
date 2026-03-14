package eu.darken.octi.common.theming

import kotlinx.coroutines.flow.Flow

interface ThemeSettings {
    val themeState: Flow<ThemeState>
}
