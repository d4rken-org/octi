package eu.darken.octi.module.core

import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.flow.Flow


interface ModuleSync<T : Any> {

    val ourDeviceId: DeviceId

    val moduleId: ModuleId

    val others: Flow<List<ModuleData<T>>>

    suspend fun sync(self: T): ModuleData<T>
}