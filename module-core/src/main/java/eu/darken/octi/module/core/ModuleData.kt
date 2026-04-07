package eu.darken.octi.module.core

import eu.darken.octi.sync.core.DeviceId
import kotlin.time.Instant

data class ModuleData<T>(
    val modifiedAt: Instant,
    val deviceId: DeviceId,
    val moduleId: ModuleId,
    val data: T,
)