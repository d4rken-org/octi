package eu.darken.octi.syrvs.jserver.core

import eu.darken.octi.sync.core.SyncDeviceId
import eu.darken.octi.sync.core.SyncRead

data class JServerDeviceData(
    override val deviceId: SyncDeviceId,
    override val modules: Collection<SyncRead.Device.Module>,
) : SyncRead.Device