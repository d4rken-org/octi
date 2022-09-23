package eu.darken.octi.modules.power.core

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.modules.ModuleId
import eu.darken.octi.modules.ModuleRepo
import eu.darken.octi.modules.ModuleSync

@InstallIn(SingletonComponent::class)
@Module
abstract class PowerModule {

    @Binds
    @IntoSet
    abstract fun sync(sync: PowerSync): ModuleSync<out Any>

    @Binds
    @IntoSet
    abstract fun repo(repo: PowerRepo): ModuleRepo<out Any>

    companion object {
        val MODULE_ID = ModuleId("${BuildConfigWrap.APPLICATION_ID}.module.core.power")
    }
}