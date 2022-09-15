package eu.darken.octi.metainfo.core

import eu.darken.octi.sync.core.DeviceId

data class SyncDataContainer<T>(
    val deviceId: DeviceId,
    val data: T,
)