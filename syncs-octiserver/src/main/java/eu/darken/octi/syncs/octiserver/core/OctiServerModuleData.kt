package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.module.core.ModuleId
import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.DeviceId
import eu.darken.octi.sync.core.SyncRead
import okio.ByteString
import kotlin.time.Instant

data class OctiServerModuleData(
    override val connectorId: ConnectorId,
    override val deviceId: DeviceId,
    override val moduleId: ModuleId,
    override val modifiedAt: Instant,
    override val payload: ByteString
) : SyncRead.Device.Module