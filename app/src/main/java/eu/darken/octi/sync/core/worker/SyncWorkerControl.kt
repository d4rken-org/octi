package eu.darken.octi.sync.core.worker

import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class SyncWorkerControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val workerManager: WorkManager,
) {

    fun schedule() = scope.launch {
        val workerData = Data.Builder().apply {

        }.build()
        log(TAG, VERBOSE) { "Worker data: $workerData" }

        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            Duration.ofMinutes(15)
        ).apply {
            setInputData(workerData)
        }.build()

        log(TAG, VERBOSE) { "Worker request: $workRequest" }

        val operation = workerManager.enqueueUniquePeriodicWork(
            "${BuildConfigWrap.APPLICATION_ID}.sync.worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )

        operation.result.get()
        log(TAG) { "SyncWorker start request send." }
    }

    companion object {
        private val TAG = logTag("Sync", "Worker", "Control")
    }
}