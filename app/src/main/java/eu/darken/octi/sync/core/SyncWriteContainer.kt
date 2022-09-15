package eu.darken.octi.sync.core

import java.util.*

data class SyncWriteContainer(
    override val writeId: UUID = UUID.randomUUID(),
    override val deviceId: DeviceId,
    override val modules: Collection<Sync.Write.Module>
) : Sync.Write