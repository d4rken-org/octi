package eu.darken.octi.modules

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.sync.core.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val moduleRepos: Set<@JvmSuppressWildcards ModuleRepo<out Any>>,
) {

    private val data = combine(moduleRepos.map { it.state }) {
        log(TAG) { "####### $it" }
        it.toList()
    }
        .replayingShare(scope + dispatcherProvider.Default)

    data class ByModule(
        val modules: Map<ModuleId, Collection<ModuleData<out Any>>>,
    )

    val byModule: Flow<ByModule> = data.map { moduleStates ->
        ByModule(
            modules = moduleStates.map { it.all }.flatten().groupBy { it.moduleId }
        )
    }

    data class ByDevice(
        val devices: Map<DeviceId, Collection<ModuleData<out Any>>>
    )

    val byDevice: Flow<ByDevice> = data.map { moduleStates ->
        ByDevice(
            devices = moduleStates.map { it.all }.flatten().groupBy { it.deviceId }
        )
    }
        .onEach {
            log(TAG) { "BYDEVICE: $it" }
        }

    fun start() {
        log(TAG) { "start()" }
    }

    companion object {
        private val TAG = logTag("Module", "Manager")
    }
}