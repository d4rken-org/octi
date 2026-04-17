package eu.darken.octi.syncs.gdrive

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
import eu.darken.octi.syncs.gdrive.core.GDriveBlobStoreHub
import eu.darken.octi.syncs.gdrive.core.GDriveHub
import eu.darken.octi.syncs.gdrive.ui.GDriveUiContribution

@InstallIn(SingletonComponent::class)
@Module
abstract class GDriveModule {

    @Binds
    @IntoSet
    abstract fun hub(hub: GDriveHub): ConnectorHub

    @Binds
    @IntoSet
    abstract fun blobHub(hub: GDriveBlobStoreHub): BlobStoreHub

    @Binds
    @IntoMap
    @ConnectorTypeKey(ConnectorType.GDRIVE)
    abstract fun uiContribution(c: GDriveUiContribution): ConnectorUiContribution
}