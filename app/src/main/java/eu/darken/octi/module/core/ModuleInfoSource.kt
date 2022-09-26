package eu.darken.octi.module.core

import kotlinx.coroutines.flow.Flow


interface ModuleInfoSource<T : Any> {
    val info: Flow<T>
}