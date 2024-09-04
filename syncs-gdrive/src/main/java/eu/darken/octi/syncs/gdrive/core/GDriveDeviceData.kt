package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead

data class GDriveDeviceData(
    override val deviceId: DeviceId,
    override val modules: Collection<SyncRead.Device.Module>,
) : SyncRead.Device