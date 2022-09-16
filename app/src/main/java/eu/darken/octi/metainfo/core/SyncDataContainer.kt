package eu.darken.octi.metainfo.core

import eu.darken.octi.sync.core.DeviceId
import java.time.Instant

data class SyncDataContainer<T>(
    val deviceId: DeviceId,
    val modifiedAt: Instant,
    val data: T,
)