package eu.darken.octi.sync.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.sync.core.SyncExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlin.time.TimeSource


@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val syncExecutor: SyncExecutor,
) : CoroutineWorker(context, params) {

    private val workerScope = SyncWorkerCoroutineScope()

    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun doWork(): Result = try {
        val start = TimeSource.Monotonic.markNow()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        doDoWork()

        val duration = start.elapsedNow()

        log(TAG, VERBOSE) { "Execution finished after ${duration.inWholeMilliseconds}ms, $inputData" }

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
        syncExecutor.execute("SyncWorker")
    }

    companion object {
        val TAG = logTag("Sync", "Worker")
    }
}
