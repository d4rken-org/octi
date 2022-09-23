package eu.darken.octi.modules

import kotlinx.coroutines.flow.Flow


interface ModuleSync<T : Any> {

    val moduleId: ModuleId

    val others: Flow<List<ModuleData<T>>>

    suspend fun sync(self: T): ModuleData<T>
}