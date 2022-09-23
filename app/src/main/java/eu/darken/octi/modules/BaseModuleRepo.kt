package eu.darken.octi.modules

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.DynamicStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.plus


abstract class BaseModuleRepo<T : Any> constructor(
    private val tag: String,
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) : ModuleRepo<T> {

    abstract val moduleId: ModuleId

    data class State<T>(
        val moduleId: ModuleId,
        val self: ModuleData<T>? = null,
        val others: Collection<ModuleData<T>> = emptySet(),
    ) : ModuleRepo.State<T> {
        override val all: Collection<ModuleData<T>>
            get() = (self?.let { listOf(it) } ?: emptyList()) + others
    }

    private val _state = DynamicStateFlow(parentScope = scope + dispatcherProvider.Default) {
        State<T>(moduleId = moduleId)
    }

    override val state: Flow<State<T>> = _state.flow

    override suspend fun updateSelf(self: ModuleData<T>) {
        log(tag) { "updateSelf(self=$self)" }
        _state.updateBlocking {
            copy(self = self)
        }
    }

    override suspend fun updateOthers(newOthers: Collection<ModuleData<T>>) {
        log(tag) { "updateOthers(newOthers=$newOthers)" }
        _state.updateBlocking {
            copy(others = newOthers)
        }
    }

}