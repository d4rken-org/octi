package eu.darken.octi.sync.core

sealed interface ConnectorCommand {

    data class DeleteDevice(val deviceId: DeviceId) : ConnectorCommand

    data class Sync(val options: SyncOptions = SyncOptions()) : ConnectorCommand

    data object Reset : ConnectorCommand

    data object Pause : ConnectorCommand

    data object Resume : ConnectorCommand
}
