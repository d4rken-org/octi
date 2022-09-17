package eu.darken.octi.sync.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow


abstract class BaseModuleRepo<T : Any> constructor(
    private val tag: String,
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : ModuleRepo<T> {

    data class State<T>(
        val self: SyncDataContainer<T>? = null,
        val others: Collection<SyncDataContainer<T>> = emptySet(),
    ) {
        val all: Collection<SyncDataContainer<T>>
            get() = (self?.let { listOf(it) } ?: emptyList()) + others
    }

    private val _state = DynamicStateFlow(parentScope = scope) {
        State<T>()
    }

    val state: Flow<State<T>> = _state.flow

    override suspend fun updateSelf(self: SyncDataContainer<T>) {
        log(tag) { "updateSelf(self=$self)" }
        _state.updateBlocking {
            copy(self = self)
        }
    }

    override suspend fun updateOthers(newOthers: Collection<SyncDataContainer<T>>) {
        log(tag) { "updateOthers(newOthers=$newOthers)" }
        _state.updateBlocking {
            copy(others = newOthers)
        }
    }

}