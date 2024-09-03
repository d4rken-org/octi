package eu.darken.octi.main.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.main.core.FossUpdateChecker
import eu.darken.octi.main.core.updater.UpdateChecker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdaterModule {

    @Binds
    @Singleton
    abstract fun updateChecker(checker: FossUpdateChecker): UpdateChecker
}