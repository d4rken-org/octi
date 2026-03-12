package eu.darken.octi.sync.core.worker

import androidx.work.*
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
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkerControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val workerManager: WorkManager,
    private val syncSettings: eu.darken.octi.sync.core.SyncSettings,
    private val upgradeRepo: UpgradeRepo,
) {

    private data class SchedulerConfig(
        val isEnabled: Boolean,
        val interval: Int,
        val onMobile: Boolean,
        val chargingEnabled: Boolean,
        val chargingInterval: Int,
        val isPro: Boolean,
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
                isPro = upgradeInfo.isPro,
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
                    Duration.ofMinutes(config.interval.coerceAtLeast(15).toLong())
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

        // Charging worker
        try {
            if (config.chargingEnabled && config.isPro) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .setRequiresCharging(true)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                    Duration.ofMinutes(config.chargingInterval.coerceAtLeast(15).toLong())
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

    companion object {
        private val WORKER_NAME = "${BuildConfigWrap.APPLICATION_ID}.sync.worker"
        private val WORKER_NAME_CHARGING = "${BuildConfigWrap.APPLICATION_ID}.sync.worker.charging"
        private val TAG = logTag("Sync", "Worker", "Control")
    }
}
