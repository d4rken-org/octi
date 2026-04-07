package eu.darken.octi.sync.core

import kotlin.time.Instant

data class DeviceMetadata(
    val deviceId: DeviceId,
    val version: String? = null,
    val platform: String? = null,
    val label: String? = null,
    val lastSeen: Instant? = null,
    val addedAt: Instant? = null,
)

val SyncConnectorState.knownDevices: Set<DeviceId>
    get() = deviceMetadata.map { it.deviceId }.toSet()
