package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.sync.core.ModuleId
import eu.darken.octi.sync.core.SyncRead
import okio.ByteString
import java.time.Instant

data class GDriveModuleData(
    override val moduleId: ModuleId,
    override val createdAt: Instant,
    override val modifiedAt: Instant,
    override val payload: ByteString
) : SyncRead.Device.Module