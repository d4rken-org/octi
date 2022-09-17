package eu.darken.octi.sync.core

import java.time.Instant

data class SyncDataContainer<T>(
    val deviceId: SyncDeviceId,
    val modifiedAt: Instant,
    val data: T,
)