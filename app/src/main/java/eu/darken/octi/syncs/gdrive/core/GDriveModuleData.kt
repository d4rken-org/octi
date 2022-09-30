package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.module.core.ModuleId
import okio.ByteString
import java.time.Instant

data class GDriveModuleData(
    override val connectorId: eu.darken.octi.sync.core.ConnectorId,
    override val deviceId: eu.darken.octi.sync.core.DeviceId,
    override val moduleId: ModuleId,
    override val createdAt: Instant,
    override val modifiedAt: Instant,
    override val payload: ByteString
) : eu.darken.octi.sync.core.SyncRead.Device.Module