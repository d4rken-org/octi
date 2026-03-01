package eu.darken.octi.main.core

import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.theming.ThemeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

val GeneralSettings.themeState: Flow<ThemeState>
    get() = combine(themeMode.flow, themeStyle.flow, themeColor.flow) { mode, style, color ->
        ThemeState(mode, style, color)
    }

val GeneralSettings.themeStateBlocking: ThemeState
    get() = ThemeState(
        themeMode.valueBlocking,
        themeStyle.valueBlocking,
        themeColor.valueBlocking,
    )
