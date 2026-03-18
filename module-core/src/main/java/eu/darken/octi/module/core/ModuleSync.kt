package eu.darken.octi.module.core

import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.flow.Flow


interface ModuleSync<T : Any> {

    enum class SyncActivity { IDLE, READING, WRITING }

    val ourDeviceId: DeviceId

    val moduleId: ModuleId

    val syncActivity: Flow<SyncActivity>

    val isSyncing: Flow<Boolean>

    val others: Flow<List<ModuleData<T>>>

    suspend fun sync(self: ModuleData<T>)
}