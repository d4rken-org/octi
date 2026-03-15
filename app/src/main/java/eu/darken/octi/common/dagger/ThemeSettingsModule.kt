package eu.darken.octi.common.dagger

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.theming.ThemeSettings
import eu.darken.octi.main.core.GeneralSettings
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeSettingsModule {

    @Binds
    @Singleton
    abstract fun bindThemeSettings(impl: GeneralSettings): ThemeSettings
}
