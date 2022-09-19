package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.sync.core.SyncModuleId
import eu.darken.octi.sync.core.SyncRead
import okio.ByteString
import java.time.Instant

data class GDriveModuleData(
    override val moduleId: SyncModuleId,
    override val createdAt: Instant,
    override val modifiedAt: Instant,
    override val payload: ByteString
) : SyncRead.Device.Module