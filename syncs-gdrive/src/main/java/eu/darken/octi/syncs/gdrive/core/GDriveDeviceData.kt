package eu.darken.octi.syncs.gdrive.core

data class GDriveDeviceData(
    override val deviceId: eu.darken.octi.sync.core.DeviceId,
    override val modules: Collection<eu.darken.octi.sync.core.SyncRead.Device.Module>,
) : eu.darken.octi.sync.core.SyncRead.Device