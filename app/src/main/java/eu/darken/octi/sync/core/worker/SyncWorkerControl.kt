package eu.darken.octi.sync.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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

            when {
                !isEnabled -> {
                    log(TAG, INFO) { "Background sync disabled, stopping all sync methods" }
                    workerManager.cancelUniqueWork(WORKER_NAME)
                    workerManager.cancelUniqueWork(CONTINUOUS_WORKER_NAME)
                }

                interval < 15 -> {
                    log(TAG, INFO) { "Using ContinuousSyncWorker for interval < 15 minutes: $interval" }
                    workerManager.cancelUniqueWork(WORKER_NAME)

                    val workData = workDataOf(ContinuousSyncWorker.EXTRA_INTERVAL to interval)
                    val workRequest = OneTimeWorkRequestBuilder<ContinuousSyncWorker>()
                        .setInputData(workData)
                        .build()

                    val operation = workerManager.enqueueUniqueWork(
                        CONTINUOUS_WORKER_NAME,
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                    val result = operation.result.get()
                    log(TAG, INFO) { "ContinuousSyncWorker scheduled: $result" }
                }

                else -> {
                    log(TAG, INFO) { "Using WorkManager for interval >= 15 minutes: $interval" }
                    workerManager.cancelUniqueWork(CONTINUOUS_WORKER_NAME)

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

                    val workRequest =
                        PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofMinutes(interval.toLong())).apply {
                            setInputData(workerData)
                            setConstraints(constraints)
                        }.build()

                    log(TAG, VERBOSE) { "Worker request: $workRequest" }

                    val operation = workerManager.enqueueUniquePeriodicWork(
                        WORKER_NAME,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        workRequest,
                    )
                    val result = operation.result.get()
                    log(TAG, INFO) { "Worker scheduled: $result" }
                }
            }
        }
            .setupCommonEventHandlers(TAG) { "scheduler" }
            .launchIn(scope)
    }

    companion object {
        private val WORKER_NAME = "${BuildConfigWrap.APPLICATION_ID}.sync.worker"
        private val CONTINUOUS_WORKER_NAME = "${BuildConfigWrap.APPLICATION_ID}.sync.worker.continuous"
        private val TAG = logTag("Sync", "Worker", "Control")
    }
}