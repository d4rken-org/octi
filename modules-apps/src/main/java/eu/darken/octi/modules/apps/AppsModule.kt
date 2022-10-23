package eu.darken.octi.modules.apps

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.module.core.ModuleSync
import eu.darken.octi.modules.apps.core.AppsRepo
import eu.darken.octi.modules.apps.core.AppsSync

@InstallIn(SingletonComponent::class)
@Module
class AppsModule {

    @Provides
    @IntoSet
    fun sync(sync: AppsSync): ModuleSync<out Any> = sync

    @Provides
    @IntoSet
    fun repo(repo: AppsRepo): ModuleRepo<out Any> = repo

    @Provides
    @IntoSet
    fun moduleId(): ModuleId = MODULE_ID

    companion object {
        val MODULE_ID = ModuleId("eu.darken.octi.module.core.apps")
    }
}