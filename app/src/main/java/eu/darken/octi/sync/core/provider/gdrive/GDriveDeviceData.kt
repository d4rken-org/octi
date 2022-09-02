package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.sync.core.Sync
import eu.darken.octi.sync.core.SyncDeviceId
import java.time.Instant

data class GDriveDeviceData(
    override val deviceId: SyncDeviceId,
    override val lastUpdatedAt: Instant,
    override val payload: String

) : Sync.Data.Device