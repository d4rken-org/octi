package eu.darken.octi.module.core

import java.time.Instant

data class ModuleData<T>(
    val modifiedAt: Instant,
    val deviceId: eu.darken.octi.sync.core.DeviceId,
    val moduleId: ModuleId,
    val data: T,
)