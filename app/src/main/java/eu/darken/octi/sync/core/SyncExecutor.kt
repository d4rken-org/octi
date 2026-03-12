package eu.darken.octi.sync.core

import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import eu.darken.octi.modules.power.ui.widget.BatteryWidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncExecutor @Inject constructor(
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    private val batteryWidgetManager: BatteryWidgetManager,
    private val powerAlertManager: PowerAlertManager,
) {

    suspend fun execute(reason: String) {
        log(TAG, INFO) { "execute() starting, reason=$reason" }
        val start = System.currentTimeMillis()

        try {
            moduleManager.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh modules: ${e.asLog()}" }
        }

        delay(3000)

        try {
            syncManager.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to sync: ${e.asLog()}" }
        }

        try {
            batteryWidgetManager.refreshWidgets()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh widgets: ${e.asLog()}" }
        }

        try {
            powerAlertManager.checkAlerts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to check alerts: ${e.asLog()}" }
        }

        val duration = System.currentTimeMillis() - start
        log(TAG, INFO) { "execute() finished in ${duration}ms, reason=$reason" }
    }

    companion object {
        private val TAG = logTag("Sync", "Executor")
    }
}
