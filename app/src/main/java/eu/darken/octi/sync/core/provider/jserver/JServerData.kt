package eu.darken.octi.sync.core.provider.jserver

import eu.darken.octi.sync.core.SyncRead
import java.util.*

data class JServerData(
    override val readId: UUID = UUID.randomUUID(),
    override val devices: Collection<SyncRead.Device> = emptySet(),
) : SyncRead