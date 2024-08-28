package eu.darken.octi.syncs.kserver

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.syncs.kserver.core.KServerHub

@InstallIn(SingletonComponent::class)
@Module
abstract class KServerModule {

    @Binds
    @IntoSet
    abstract fun hub(hub: KServerHub): ConnectorHub
}