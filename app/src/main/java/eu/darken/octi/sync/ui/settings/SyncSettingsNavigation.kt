package eu.darken.octi.sync.ui.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.common.navigation.Nav
import eu.darken.octi.common.navigation.NavigationEntry
import javax.inject.Inject

class SyncSettingsNavigation @Inject constructor() : NavigationEntry {
    override fun EntryProviderScope<NavKey>.setup() {
        entry<Nav.Settings.Sync> { SyncSettingsScreenHost() }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds
        @IntoSet
        abstract fun bind(entry: SyncSettingsNavigation): NavigationEntry
    }
}
