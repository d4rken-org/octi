package eu.darken.octi.syncs.octiserver

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import eu.darken.octi.common.sync.ConnectorType
import eu.darken.octi.sync.core.ConnectorTypeKey
import eu.darken.octi.sync.core.ConnectorUiContribution
import eu.darken.octi.sync.core.ConnectorHub
import eu.darken.octi.sync.core.blob.BlobStoreHub
import eu.darken.octi.syncs.octiserver.core.OctiServerBlobStoreHub
import eu.darken.octi.syncs.octiserver.core.OctiServerHub
import eu.darken.octi.syncs.octiserver.ui.OctiServerUiContribution

@InstallIn(SingletonComponent::class)
@Module
abstract class OctiServerModule {

    @Binds
    @IntoSet
    abstract fun hub(hub: OctiServerHub): ConnectorHub

    @Binds
    @IntoSet
    abstract fun blobHub(hub: OctiServerBlobStoreHub): BlobStoreHub

    @Binds
    @IntoMap
    @ConnectorTypeKey(ConnectorType.OCTISERVER)
    abstract fun uiContribution(c: OctiServerUiContribution): ConnectorUiContribution
}