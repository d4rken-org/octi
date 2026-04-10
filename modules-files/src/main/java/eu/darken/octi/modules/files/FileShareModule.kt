package eu.darken.octi.modules.files

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.module.core.ModuleRepo
import eu.darken.octi.module.core.ModuleSync
import eu.darken.octi.modules.files.core.FileShareRepo
import eu.darken.octi.modules.files.core.FileShareSync

@InstallIn(SingletonComponent::class)
@Module
class FileShareModule {

    @Provides
    @IntoSet
    fun sync(sync: FileShareSync): ModuleSync<out Any> = sync

    @Provides
    @IntoSet
    fun repo(repo: FileShareRepo): ModuleRepo<out Any> = repo

    @Provides
    @IntoSet
    fun moduleId(): ModuleId = MODULE_ID

    companion object {
        val MODULE_ID = ModuleId("eu.darken.octi.module.core.files")
    }
}
