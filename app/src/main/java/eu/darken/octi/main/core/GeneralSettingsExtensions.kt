package eu.darken.octi.main.core

import eu.darken.octi.common.datastore.valueBlocking
import eu.darken.octi.common.theming.ThemeState

val GeneralSettings.themeStateBlocking: ThemeState
    get() = ThemeState(
        themeMode.valueBlocking,
        themeStyle.valueBlocking,
        themeColor.valueBlocking,
    )
