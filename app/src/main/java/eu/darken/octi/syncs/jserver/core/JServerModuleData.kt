package eu.darken.octi.syncs.jserver.core

import eu.darken.octi.modules.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead
import okio.ByteString
import java.time.Instant

data class JServerModuleData(
    override val accountId: ConnectorId,
    override val deviceId: DeviceId,
    override val moduleId: ModuleId,
    override val createdAt: Instant,
    override val modifiedAt: Instant,
    override val payload: ByteString
) : SyncRead.Device.Module