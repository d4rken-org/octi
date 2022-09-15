package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.Sync

data class GDriveDeviceData(
    override val deviceId: DeviceId,
    override val modules: Collection<Sync.Read.Module>,
) : Sync.Read.Device