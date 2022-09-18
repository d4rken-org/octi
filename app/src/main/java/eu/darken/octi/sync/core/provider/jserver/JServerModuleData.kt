package eu.darken.octi.sync.core.provider.jserver

import eu.darken.octi.sync.core.SyncModuleId
import eu.darken.octi.sync.core.SyncRead
import okio.ByteString
import java.time.Instant

data class JServerModuleData(
    override val moduleId: SyncModuleId,
    override val createdAt: Instant,
    override val modifiedAt: Instant,
    override val payload: ByteString
) : SyncRead.Device.Module