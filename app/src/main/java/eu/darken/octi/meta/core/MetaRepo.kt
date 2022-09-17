package eu.darken.octi.meta.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.sync.core.SyncDataContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) {

    data class State(
        val self: SyncDataContainer<MetaInfo>? = null,
        val others: Collection<SyncDataContainer<MetaInfo>> = emptySet(),
    ) {
        val all: Collection<SyncDataContainer<MetaInfo>>
            get() = (self?.let { listOf(it) } ?: emptyList()) + others
    }

    private val _state = DynamicStateFlow(parentScope = scope) {
        State()
    }

    val state: Flow<State> = _state.flow

    suspend fun updateSelf(self: SyncDataContainer<MetaInfo>) {
        log(TAG) { "updateSelf(self=$self)" }
        _state.updateBlocking {
            copy(self = self)
        }
    }

    suspend fun updateOthers(newOthers: Collection<SyncDataContainer<MetaInfo>>) {
        log(TAG) { "updateOthers(newOthers=$newOthers)" }
        _state.updateBlocking {
            copy(others = newOthers)
        }
    }

    companion object {
        val TAG = logTag("Module", "Meta", "Repo")
    }
}