package eu.darken.octi.modules.clipboard

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.module.core.ModuleSync

@InstallIn(SingletonComponent::class)
@Module
class ClipboardModule {

    @Provides
    @IntoSet
    fun sync(sync: ClipboardSync): ModuleSync<out Any> = sync

    @Provides
    @IntoSet
    fun repo(repo: ClipboardRepo): ModuleRepo<out Any> = repo

    @Provides
    @IntoSet
    fun moduleId(): ModuleId = MODULE_ID

    companion object {
        val MODULE_ID = ModuleId("eu.darken.octi.module.core.clipboard")
    }
}