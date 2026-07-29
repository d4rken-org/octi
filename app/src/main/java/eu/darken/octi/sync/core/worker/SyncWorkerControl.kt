package eu.darken.octi.sync.core.worker

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.Bugs
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.flow.shareLatest
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaDuration

@Singleton
class SyncWorkerControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val workerManager: WorkManager,
    private val syncSettings: eu.darken.octi.sync.core.SyncSettings,
    private val upgradeRepo: UpgradeRepo,
) {

    data class WorkerState(
        val defaultWorker: WorkerInfo,
        val chargingWorker: WorkerInfo,
    ) {
        data class WorkerInfo(
            val isEnabled: Boolean,
            val isRunning: Boolean,
            val isBlocked: Boolean,
            val nextRunAt: Instant?,
        )
    }

    val workerState: Flow<WorkerState> = kotlinx.coroutines.flow.combine(
        workerManager.getWorkInfosForUniqueWorkFlow(WORKER_NAME),
        workerManager.getWorkInfosForUniqueWorkFlow(WORKER_NAME_CHARGING),
    ) { defaultInfos, chargingInfos ->
        WorkerState(
            defaultWorker = defaultInfos.firstOrNull().toWorkerInfo(),
            chargingWorker = chargingInfos.firstOrNull().toWorkerInfo(),
        )
    }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "workerState" }
        .shareLatest(scope)

    /**
     * Reactive, three-way read of the Pro entitlement for scheduling. [UNDETERMINED] is distinct
     * from [FREE] on purpose: on a GPlay cold start (or a billing error) the entitlement is not yet
     * definitive, and treating that as free would cancel a paying user's charging worker until
     * billing settles. Only a settled, error-free non-Pro state is a real [FREE].
     */
    private enum class ProEntitlement { PRO, FREE, UNDETERMINED }

    private fun UpgradeRepo.Info.toEntitlement(): ProEntitlement = when {
        isPro -> ProEntitlement.PRO
        error != null || !isSettled -> ProEntitlement.UNDETERMINED
        else -> ProEntitlement.FREE
    }

    private data class SchedulerConfig(
        val isEnabled: Boolean,
        val interval: Int,
        val onMobile: Boolean,
        val chargingEnabled: Boolean,
        val chargingInterval: Int,
        val entitlement: ProEntitlement,
    )

    fun start() {
        log(TAG) { "start()" }
        combine(
            syncSettings.backgroundSyncEnabled.flow,
            syncSettings.backgroundSyncInterval.flow,
            syncSettings.backgroundSyncOnMobile.flow,
            syncSettings.backgroundSyncChargingEnabled.flow,
            syncSettings.backgroundSyncChargingInterval.flow,
            upgradeRepo.upgradeInfo,
        ) { isEnabled, interval, onMobile, chargingEnabled, chargingInterval, upgradeInfo ->
            SchedulerConfig(
                isEnabled = isEnabled,
                interval = interval,
                onMobile = onMobile,
                chargingEnabled = chargingEnabled,
                chargingInterval = chargingInterval,
                entitlement = upgradeInfo.toEntitlement(),
            )
        }
            .distinctUntilChanged()
            .onEach { config -> applySchedulerConfig(config) }
            .setupCommonEventHandlers(TAG) { "scheduler" }
            .launchIn(scope)
    }

    private fun applySchedulerConfig(config: SchedulerConfig) {
        log(TAG) { "applySchedulerConfig: $config" }

        val networkType = if (config.onMobile) NetworkType.CONNECTED else NetworkType.UNMETERED

        // Default background worker
        try {
            if (config.isEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                    config.interval.coerceAtLeast(15).minutes.toJavaDuration()
                ).apply {
                    setConstraints(constraints)
                }.build()

                log(TAG, VERBOSE) { "Default worker request: $workRequest" }

                workerManager.enqueueUniquePeriodicWork(
                    WORKER_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest,
                )
                log(TAG, INFO) { "Default worker enqueued" }
            } else {
                workerManager.cancelUniqueWork(WORKER_NAME)
                log(TAG, INFO) { "Default worker canceled." }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Default worker operation failed: ${e.asLog()}" }
            Bugs.report(e)
        }

        // Charging worker (Pro-gated). Entitlement drives a three-way decision so a not-yet-settled
        // billing state can't cancel a paying user's charging sync on a cold start.
        if (config.entitlement == ProEntitlement.UNDETERMINED) {
            // Not definitive yet (cold-start seed or billing error): preserve whatever charging work
            // already exists until the entitlement resolves one way or the other.
            log(TAG, INFO) { "Charging worker: entitlement undetermined, preserving existing work." }
        } else {
            try {
                if (config.chargingEnabled && config.entitlement == ProEntitlement.PRO) {
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(networkType)
                        .setRequiresCharging(true)
                        .build()

                    val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                        config.chargingInterval.coerceAtLeast(15).minutes.toJavaDuration()
                    ).apply {
                        setConstraints(constraints)
                    }.build()

                    log(TAG, VERBOSE) { "Charging worker request: $workRequest" }

                    workerManager.enqueueUniquePeriodicWork(
                        WORKER_NAME_CHARGING,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest,
                    )
                    log(TAG, INFO) { "Charging worker enqueued" }
                } else {
                    workerManager.cancelUniqueWork(WORKER_NAME_CHARGING)
                    log(TAG, INFO) { "Charging worker canceled." }
                }
            } catch (e: Exception) {
                log(TAG, ERROR) { "Charging worker operation failed: ${e.asLog()}" }
                Bugs.report(e)
            }
        }
    }

    companion object {
        internal val WORKER_NAME = "${BuildConfigWrap.APPLICATION_ID}.sync.worker"
        internal val WORKER_NAME_CHARGING = "${BuildConfigWrap.APPLICATION_ID}.sync.worker.charging"
        private val TAG = logTag("Sync", "Worker", "Control")

        internal fun WorkInfo?.toWorkerInfo(): WorkerState.WorkerInfo {
            if (this == null) return WorkerState.WorkerInfo(
                isEnabled = false,
                isRunning = false,
                isBlocked = false,
                nextRunAt = null,
            )
            val isActive = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
            val nextMs = nextScheduleTimeMillis
            return WorkerState.WorkerInfo(
                isEnabled = isActive,
                isRunning = state == WorkInfo.State.RUNNING,
                isBlocked = state == WorkInfo.State.BLOCKED,
                nextRunAt = if (nextMs != Long.MAX_VALUE && nextMs > 0) Instant.fromEpochMilliseconds(nextMs) else null,
            )
        }
    }
}
