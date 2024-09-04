package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull


inline fun <reified T : SyncConnector> SyncManager.getConnectorById(identifier: ConnectorId): Flow<T> {
    return connectors.map { connecs -> connecs.single { it.identifier == identifier } }.map { it as T }
}

fun Collection<SyncRead>.latestData(): Collection<SyncRead.Device> = this
    .asSequence()
    .map { it.devices }.flatten()
    .map { it.modules }.flatten()
    .groupBy { it.deviceId }
    .map { (deviceId, modules) ->
        val newestModules = modules
            .groupBy { it.moduleId }
            .map { keyVal ->
                keyVal.key to keyVal.value.maxByOrNull { it.modifiedAt }!!
            }
            .toMap()

        object : SyncRead.Device {
            override val deviceId: DeviceId = deviceId

            override val modules: Collection<SyncRead.Device.Module> = newestModules.values
        }
    }
    .toList()

suspend inline fun <reified T : SyncConnector> ConnectorHub.findConnector(crossinline matcher: (T) -> Boolean): T {
    return connectors.mapNotNull { connectors ->
        connectors.map { it as T }.singleOrNull { matcher(it) }
    }.first()
}