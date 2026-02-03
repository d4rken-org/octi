package eu.darken.octi.sync.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.module.core.ModuleManager
import eu.darken.octi.modules.power.core.alert.PowerAlertManager
import eu.darken.octi.modules.power.ui.widget.BatteryWidgetManager
import eu.darken.octi.sync.core.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay


@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
//    syncWorkerComponentBuilder: SyncWorkerComponent.Builder,
    private val syncManager: SyncManager,
    private val moduleManager: ModuleManager,
    private val batteryWidgetManager: BatteryWidgetManager,
    private val powerAlertManager: PowerAlertManager,
) : CoroutineWorker(context, params) {

    private val workerScope = SyncWorkerCoroutineScope()
//    private val monitorComponent = syncWorkerComponentBuilder
//        .coroutineScope(workerScope)
//        .build()
//
//    private val entryPoint by lazy {
//        EntryPoints.get(monitorComponent, SyncWorkerEntryPoint::class.java)
//    }

    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        doDoWork()

        val duration = System.currentTimeMillis() - start

        log(TAG, VERBOSE) { "Execution finished after ${duration}ms, $inputData" }

        Result.success(inputData)
    } catch (e: Exception) {
        if (e !is CancellationException) {
            Bugs.report(e)
            finishedWithError = true
            Result.failure(inputData)
        } else {
            Result.success()
        }
    } finally {
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork() {
        try {
            moduleManager.refresh()
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh modules: ${e.asLog()}" }
        }

        delay(3000)

        try {
            syncManager.sync()
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to sync: ${e.asLog()}" }
        }

        try {
            batteryWidgetManager.refreshWidgets()
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to refresh widgets: ${e.asLog()}" }
        }

        try {
            powerAlertManager.checkAlerts()
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to check alerts: ${e.asLog()}" }
        }
    }

    companion object {
        val TAG = logTag("Sync", "Worker")
    }
}
