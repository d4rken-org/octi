package eu.darken.octi.sync.core

import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.widget.WidgetManager
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Singleton
class SyncExecutor @Inject constructor(
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
    private val powerAlertManager: PowerAlertManager,
) {

    suspend fun execute(reason: String) {
        log(TAG, INFO) { "execute() starting, reason=$reason" }
        val start = TimeSource.Monotonic.markNow()

        try {
            moduleManager.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh modules: ${e.asLog()}" }
        }

        delay(3.seconds)

        try {
            syncManager.sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to sync: ${e.asLog()}" }
        }

        for (manager in widgetManagers) {
            try {
                manager.refreshWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to refresh widgets: ${e.asLog()}" }
            }
        }

        try {
            powerAlertManager.checkAlerts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to check alerts: ${e.asLog()}" }
        }

        val duration = start.elapsedNow()
        log(TAG, INFO) { "execute() finished in ${duration.inWholeMilliseconds}ms, reason=$reason" }
    }

    companion object {
        private val TAG = logTag("Sync", "Executor")
    }
}
