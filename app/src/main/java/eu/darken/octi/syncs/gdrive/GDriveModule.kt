package eu.darken.octi.syncs.gdrive

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.syncs.gdrive.core.GDriveHub

@InstallIn(SingletonComponent::class)
@Module
abstract class GDriveModule {

    @Binds
    @IntoSet
    abstract fun hub(hub: GDriveHub): eu.darken.octi.sync.core.ConnectorHub
}