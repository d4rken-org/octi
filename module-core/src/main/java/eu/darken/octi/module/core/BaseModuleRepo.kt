package eu.darken.octi.module.core

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.WARN
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import java.util.*


abstract class BaseModuleRepo<T : Any> constructor(
    private val tag: String,
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val moduleSettings: ModuleSettings,
    private val moduleSync: ModuleSync<T>,
    private val infoSource: ModuleInfoSource<T>,
) : ModuleRepo<T> {

    abstract val moduleId: ModuleId

    data class State<T>(
        val moduleId: ModuleId,
        val isSelfLoaded: Boolean = false,
        val self: ModuleData<T>? = null,
        val isOthersLoaded: Boolean = false,
        val others: Collection<ModuleData<T>> = emptySet(),
    ) : ModuleRepo.State<T> {

        val isInitialized: Boolean
            get() = isSelfLoaded && isOthersLoaded

        override val all: Collection<ModuleData<T>>
            get() = (self?.let { listOf(it) } ?: emptyList()) + others
    }

    private var initializeStart = 0L
    private var initializeStop = 0L
    private val _state = DynamicStateFlow(parentScope = scope + dispatcherProvider.Default) {
        State<T>(moduleId = moduleId).also {
            log(tag) { "PERF: Initializing START, new state, not initialized." }
            initializeStart = System.currentTimeMillis()
        }
    }

    override val state: Flow<State<T>> = _state.flow

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    override suspend fun updateSelf(self: ModuleData<T>?) {
        log(tag) { "updateSelf(self=$self)" }
        val result = _state.updateBlocking {
            copy(
                self = self,
                isSelfLoaded = true,
            )
        }
        if (result.isInitialized && initializeStop == 0L) {
            initializeStop = System.currentTimeMillis()
            log(tag) { "PERF: Initializing DONE, self and others loaded (${initializeStop - initializeStart}ms)." }
        }
    }

    override suspend fun updateOthers(newOthers: Collection<ModuleData<T>>) {
        log(tag) { "updateOthers(newOthers=$newOthers)" }
        val result = _state.updateBlocking {
            copy(
                others = newOthers,
                isOthersLoaded = true,
            )
        }
        if (result.isInitialized && initializeStop == 0L) {
            initializeStop = System.currentTimeMillis()
            log(tag) { "PERF: Initializing DONE, self and others loaded (${initializeStop - initializeStart}ms)." }
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
        .onEach { selfData ->
            updateSelf(selfData?.let { moduleSync.sync(it) })
        }
        .setupCommonEventHandlers(tag) { "writeFLow" }

    override val keepAlive: Flow<Unit> = combine(
        readFlow,
        writeFLow
    ) { _, _ -> Unit }
        .setupCommonEventHandlers(tag) { "keepAlive" }
        .replayingShare(scope + dispatcherProvider.Default)
}