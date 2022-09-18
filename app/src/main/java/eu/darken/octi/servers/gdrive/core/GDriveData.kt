package eu.darken.octi.servers.gdrive.core

import eu.darken.octi.sync.core.SyncRead
import java.util.*

data class GDriveData(
    override val readId: UUID = UUID.randomUUID(),
    override val devices: Collection<SyncRead.Device> = emptySet(),
) : SyncRead