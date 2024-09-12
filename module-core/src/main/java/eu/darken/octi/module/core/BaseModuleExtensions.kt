package eu.darken.octi.module.core

import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.flow.first


suspend fun <T : Any> BaseModuleRepo<T>.current() = this.state.first()

suspend fun <T : Any> BaseModuleRepo<T>.device(
    deviceId: DeviceId
): ModuleData<T>? = this.current().all.firstOrNull { it.deviceId == deviceId }