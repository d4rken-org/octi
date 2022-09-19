package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.sync.core.SyncDeviceId
import eu.darken.octi.sync.core.SyncRead

data class GDriveDeviceData(
    override val deviceId: SyncDeviceId,
    override val modules: Collection<SyncRead.Device.Module>,
) : SyncRead.Device