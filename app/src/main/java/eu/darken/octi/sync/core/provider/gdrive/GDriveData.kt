package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.sync.core.Sync

data class GDriveData(
    override val devices: Collection<Sync.Data.Device>
) : Sync.Data