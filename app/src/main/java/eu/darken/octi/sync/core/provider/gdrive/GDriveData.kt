package eu.darken.octi.sync.core.provider.gdrive

import eu.darken.octi.sync.core.Sync
import java.util.*

data class GDriveData(
    override val readId: UUID = UUID.randomUUID(),
    override val devices: Collection<Sync.Read.Device>,
) : Sync.Read