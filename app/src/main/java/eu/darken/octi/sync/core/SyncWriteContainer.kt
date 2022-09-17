package eu.darken.octi.sync.core

import java.util.*

data class SyncWriteContainer(
    override val writeId: UUID = UUID.randomUUID(),
    override val deviceId: SyncDeviceId,
    override val modules: Collection<SyncWrite.Module>
) : SyncWrite