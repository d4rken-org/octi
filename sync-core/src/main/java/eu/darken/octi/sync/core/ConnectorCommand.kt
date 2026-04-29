package eu.darken.octi.sync.core

sealed interface ConnectorCommand {

    data class DeleteDevice(val deviceId: DeviceId) : ConnectorCommand

    data class Sync(val options: SyncOptions = SyncOptions()) : ConnectorCommand

    data object Reset : ConnectorCommand

    data class Pause(val reason: ConnectorPauseReason = ConnectorPauseReason.Manual) : ConnectorCommand

    data object Resume : ConnectorCommand
}
