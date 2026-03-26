package eu.darken.octi.syncs.octiserver

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.syncs.octiserver.core.OctiServerHub

@InstallIn(SingletonComponent::class)
@Module
abstract class OctiServerModule {

    @Binds
    @IntoSet
    abstract fun hub(hub: OctiServerHub): ConnectorHub
}