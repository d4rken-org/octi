package eu.darken.octi.syncs.octiserver.core

import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncRead

data class OctiServerData(
    override val connectorId: ConnectorId,
    override val devices: Collection<SyncRead.Device> = emptySet(),
) : SyncRead