package eu.darken.octi.sync.core

import kotlinx.coroutines.flow.Flow


interface ModuleInfoSource<T : Any> {
    val info: Flow<T>
}