package eu.darken.octi.modules

import kotlinx.coroutines.flow.Flow


interface ModuleInfoSource<T : Any> {
    val info: Flow<T>
}