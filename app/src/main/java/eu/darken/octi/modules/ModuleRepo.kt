package eu.darken.octi.modules

import kotlinx.coroutines.flow.Flow

interface ModuleRepo<T> {

    interface State<T> {
        val all: Collection<ModuleData<T>>
    }

    val state: Flow<State<T>>

    suspend fun updateSelf(self: ModuleData<T>)

    suspend fun updateOthers(newOthers: Collection<ModuleData<T>>)
}