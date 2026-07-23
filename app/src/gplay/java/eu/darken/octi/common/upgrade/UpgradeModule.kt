package eu.darken.octi.common.upgrade

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.upgrade.core.UpgradeRepoGplay
import javax.inject.Singleton
import kotlin.time.Clock

@InstallIn(SingletonComponent::class)
@Module
abstract class UpgradeModule {
    @Binds
    @Singleton
    abstract fun control(gplay: UpgradeRepoGplay): UpgradeRepo

    companion object {
        @Provides
        fun clock(): Clock = Clock.System
    }
}