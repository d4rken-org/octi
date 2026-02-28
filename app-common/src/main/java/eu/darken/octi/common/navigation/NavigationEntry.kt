package eu.darken.octi.common.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

interface NavigationEntry {
    fun EntryProviderScope<NavKey>.setup()

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class BindsModule {
        @Multibinds
        abstract fun entries(): Set<NavigationEntry>
    }
}
