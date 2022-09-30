package eu.darken.octi.syncs.jserver.core

import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncRead

data class JServerData(
    override val connectorId: ConnectorId,
    override val devices: Collection<SyncRead.Device> = emptySet(),
) : SyncRead