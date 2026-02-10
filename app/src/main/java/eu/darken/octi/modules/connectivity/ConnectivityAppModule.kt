package eu.darken.octi.modules.connectivity

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.common.network.PublicIpEndpointProvider
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class ConnectivityAppModule {

    @Binds
    @Singleton
    abstract fun publicIpEndpointProvider(impl: PublicIpEndpointProviderImpl): PublicIpEndpointProvider
}
