package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead

data class OctiServerDeviceData(
    override val deviceId: DeviceId,
    override val modules: Collection<SyncRead.Device.Module>,
) : SyncRead.Device