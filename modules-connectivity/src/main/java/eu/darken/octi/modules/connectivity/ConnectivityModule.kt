package eu.darken.octi.modules.connectivity

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.module.core.ModuleSync
import eu.darken.octi.modules.connectivity.core.ConnectivityRepo
import eu.darken.octi.modules.connectivity.core.ConnectivitySync

@InstallIn(SingletonComponent::class)
@Module
class ConnectivityModule {

    @Provides
    @IntoSet
    fun sync(sync: ConnectivitySync): ModuleSync<out Any> = sync

    @Provides
    @IntoSet
    fun repo(repo: ConnectivityRepo): ModuleRepo<out Any> = repo

    @Provides
    @IntoSet
    fun moduleId(): ModuleId = MODULE_ID

    companion object {
        val MODULE_ID = ModuleId("eu.darken.octi.module.core.connectivity")
    }
}
