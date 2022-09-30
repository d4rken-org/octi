package eu.darken.octi.syncs.gdrive.core

data class GDriveData(
    override val connectorId: eu.darken.octi.sync.core.ConnectorId,
    override val devices: Collection<eu.darken.octi.sync.core.SyncRead.Device>,
) : eu.darken.octi.sync.core.SyncRead