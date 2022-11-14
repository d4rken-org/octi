package eu.darken.octi.module.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import java.time.Instant
import java.util.*


abstract class BaseModuleRepo<T : Any> constructor(
    private val tag: String,
    @AppScope private val scope: CoroutineScope,
    private val moduleId: ModuleId,
    dispatcherProvider: DispatcherProvider,
    moduleSettings: ModuleSettings,
    private val moduleSync: ModuleSync<T>,
    private val infoSource: ModuleInfoSource<T>,
    private val moduleCache: ModuleCache<T>,
) : ModuleRepo<T> {

    data class State<T>(
        val moduleId: ModuleId,
        val self: ModuleData<T>? = null,
        val isOthersInitialized: Boolean = false,
        val others: Collection<ModuleData<T>> = emptySet(),
    ) : ModuleRepo.State<T> {

        override val all: Collection<ModuleData<T>>
            get() = (self?.let { listOf(it) } ?: emptyList()) + others
    }

    private val _state = DynamicStateFlow(parentScope = scope + dispatcherProvider.Default) {
        State(
            moduleId = moduleId,
            self = moduleCache.get(moduleSync.ourDeviceId),
            others = (moduleCache.cachedDevices() - moduleSync.ourDeviceId).mapNotNull { moduleCache.get(it) },
        )
    }

    override val state: Flow<State<T>> = _state.flow

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    override suspend fun updateSelf(self: ModuleData<T>?) {
        log(tag) { "updateSelf(self=$self)" }
        _state.updateBlocking {
            moduleCache.set(moduleSync.ourDeviceId, self)
            copy(self = self)
        }
    }

    override suspend fun updateOthers(newOthers: Collection<ModuleData<T>>) {
        log(tag) { "updateOthers(newOthers=$newOthers)" }
        _state.updateBlocking {
            // Delete all except ourself, then set with new known data, to remove stale data
            (moduleCache.cachedDevices() - moduleSync.ourDeviceId).forEach { moduleCache.set(it, null) }
            newOthers.forEach { moduleCache.set(it.deviceId, it) }
            copy(others = newOthers)
        }
    }

    override suspend fun refresh() {
        log(tag) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
    }

    private val readFlow = moduleSettings.isEnabled.flow
        .flatMapLatest { isEnabled ->
            if (!isEnabled) {
                log(tag, WARN) { "$moduleId is disabled" }
                flowOf(emptyList())
            } else {
                moduleSync.others
            }
        }
        .onEach { othersData ->
            updateOthers(othersData)
        }
        .setupCommonEventHandlers(tag) { "readFlow" }

    private val writeFLow = moduleSettings.isEnabled.flow
        .flatMapLatest { isEnabled ->
            if (!isEnabled) {
                log(tag, WARN) { "$moduleId is disabled" }
                flowOf(null)
            } else {
                refreshTrigger.flatMapLatest { infoSource.info }
            }
        }
        .distinctUntilChanged()
        .map {
            if (it == null) return@map null
            ModuleData(
                modifiedAt = Instant.now(),
                deviceId = moduleSync.ourDeviceId,
                moduleId = moduleId,
                data = it
            )
        }
        .onEach { selfData ->
            try {
                selfData?.let { moduleSync.sync(selfData) }
            } catch (e: Exception) {
                log(tag, ERROR) { "Failed to sync data: ${e.asLog()}" }
                Bugs.report(e)
            }
            updateSelf(selfData)
        }
        .setupCommonEventHandlers(tag) { "writeFLow" }

    override val keepAlive: Flow<Unit> = combine(
        readFlow,
        writeFLow
    ) { _, _ -> }
        .setupCommonEventHandlers(tag) { "keepAlive" }
        .replayingShare(scope + dispatcherProvider.Default)
}