package eu.darken.octi.syncs.gdrive.core

import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncRead

data class GDriveData(
    override val connectorId: ConnectorId,
    override val devices: Collection<SyncRead.Device>,
) : SyncRead