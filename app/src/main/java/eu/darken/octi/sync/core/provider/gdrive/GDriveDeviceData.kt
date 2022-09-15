package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.sync.core.Sync
import eu.darken.octi.sync.core.SyncDeviceId

data class GDriveDeviceData(
    override val deviceId: SyncDeviceId,
    override val modules: Collection<Sync.Read.Module>,
) : Sync.Read.Device