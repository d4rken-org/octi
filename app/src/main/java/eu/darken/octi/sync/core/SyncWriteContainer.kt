package eu.darken.octi.sync.core

import java.util.*

data class SyncWriteContainer(
    override val writeId: UUID = UUID.randomUUID(),
    override val userId: UserId,
    override val deviceId: DeviceId,
    override val modules: Collection<Sync.Module>
) : Sync.Write