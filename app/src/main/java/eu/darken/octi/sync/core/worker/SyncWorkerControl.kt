package eu.darken.octi.sync.core.worker

import androidx.work.*
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("BlockingMethodInNonBlockingContext")
@Singleton
class SyncWorkerControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val workerManager: WorkManager,
    private val syncSettings: eu.darken.octi.sync.core.SyncSettings,
) {

    fun start() {
        log(TAG) { "start()" }
        combine(
            syncSettings.backgroundSyncEnabled.flow,
            syncSettings.backgroundSyncInterval.flow,
            syncSettings.backgroundSyncOnMobile.flow
        ) { isEnabled, interval, onMobile ->
            log(TAG) { "SyncSettings: isEnabled=$isEnabled, interval=$interval, onMobile=$onMobile" }

            val workerData = Data.Builder().apply {

            }.build()

            log(TAG, VERBOSE) { "Worker data: $workerData" }
            val constraints = Constraints.Builder().apply {
                if (onMobile) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                } else {
                    setRequiredNetworkType(NetworkType.UNMETERED)

                }
            }.build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofMinutes(interval.toLong())).apply {
                setInputData(workerData)
                setConstraints(constraints)
            }.build()

            log(TAG, VERBOSE) { "Worker request: $workRequest" }

            if (isEnabled) {
                val operation = workerManager.enqueueUniquePeriodicWork(
                    WORKER_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest,
                )
                val result = operation.result.get()

                log(TAG, INFO) { "Worker scheduled: $result" }
            } else {
                workerManager.cancelUniqueWork(WORKER_NAME)
                log(TAG, INFO) { "Worker canceled." }
            }
        }
            .setupCommonEventHandlers(TAG) { "scheduler" }
            .launchIn(scope)
    }

    companion object {
        private val WORKER_NAME = "${BuildConfigWrap.APPLICATION_ID}.sync.worker"
        private val TAG = logTag("Sync", "Worker", "Control")
    }
}