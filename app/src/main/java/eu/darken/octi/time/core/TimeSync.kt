package eu.darken.octi.time.core

import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.sync.core.ModuleId
import eu.darken.octi.sync.core.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeSync @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val syncManager: SyncManager,
    private val timeRepo: TimeRepo,
    private val timeSettings: TimeSettings,
) {

    fun start() {
        log(TAG) { "start()" }

        // Read
        timeSettings.isSyncEnabled.flow
            .flatMapLatest { isEnabled ->
                log(TAG) { "SyncRead: isEnabled=$isEnabled" }
                if (!isEnabled) return@flatMapLatest emptyFlow()
                else syncManager.data
            }
            .map { reads ->
                reads
                    .map { it.devices }.flatten()
                    .map { device ->
                        device.modules
                            .filter { it.moduleId == MODULE_ID }
                            .map { TimeSyncData.from(it) }
                            .map { device to it }
                    }
                    .flatten()
            }
            .onEach {
                log(TAG, VERBOSE) { "SyncRead: Processing new data: $it" }
            }
            .setupCommonEventHandlers(TAG) { "syncRead" }
            .launchIn(scope + dispatcherProvider.IO)

        // Write
        timeSettings.isSyncEnabled.flow
            .flatMapLatest { isEnabled ->
                log(TAG) { "SyncWrite: isEnabled=$isEnabled" }
                if (!isEnabled) return@flatMapLatest emptyFlow()
                else timeRepo.time
            }
            .map { info ->
                TimeSyncData(
                    deviceTime = info.deviceTime
                )
            }
            .onEach {
                log(TAG, VERBOSE) { "SyncWrite: Processing new data: $it" }
                syncManager.write(it.toSyncWrite())
            }
            .setupCommonEventHandlers(TAG) { "syncWrite" }
            .launchIn(scope + dispatcherProvider.IO)
    }

    companion object {
        val MODULE_ID = ModuleId("${BuildConfigWrap.APPLICATION_ID}.module.core.time")
        val TAG = logTag("Module", "Time", "Sync")
    }
}