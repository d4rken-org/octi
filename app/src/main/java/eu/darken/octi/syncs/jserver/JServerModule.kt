package eu.darken.octi.syncs.jserver

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.syncs.jserver.core.JServerHub

@InstallIn(SingletonComponent::class)
@Module
abstract class JServerModule {

    @Binds
    @IntoSet
    abstract fun hub(hub: JServerHub): ConnectorHub
}