package eu.darken.octi.module.core

import eu.darken.octi.sync.core.DeviceId

interface ModuleCache<T> {

    val moduleId: ModuleId

    suspend fun get(deviceId: DeviceId): ModuleData<T>?
    suspend fun set(deviceId: DeviceId, data: ModuleData<T>?)

    suspend fun cachedDevices(): Collection<DeviceId>
}
