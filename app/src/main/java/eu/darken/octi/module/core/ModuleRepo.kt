package eu.darken.octi.module.core

import kotlinx.coroutines.flow.Flow

interface ModuleRepo<T> {

    interface State<T> {
        val all: Collection<ModuleData<T>>
    }

    val state: Flow<State<T>>

    val keepAlive: Flow<Unit>

    suspend fun updateSelf(self: ModuleData<T>?)

    suspend fun updateOthers(newOthers: Collection<ModuleData<T>>)
}