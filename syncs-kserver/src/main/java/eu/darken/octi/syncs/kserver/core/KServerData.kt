package eu.darken.octi.syncs.kserver.core

import eu.darken.octi.sync.core.ConnectorId
import eu.darken.octi.sync.core.SyncRead

data class KServerData(
    override val connectorId: ConnectorId,
    override val devices: Collection<SyncRead.Device> = emptySet(),
) : SyncRead