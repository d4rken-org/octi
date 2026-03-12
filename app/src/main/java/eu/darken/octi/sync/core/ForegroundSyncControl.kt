package eu.darken.octi.sync.core

import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.combine
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundSyncControl @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val syncExecutor: SyncExecutor,
    private val syncSettings: SyncSettings,
    private val upgradeRepo: UpgradeRepo,
    private val networkStateProvider: NetworkStateProvider,
) {

    @Volatile private var lastRunAt: Long = 0L

    fun start() {
        log(TAG) { "start()" }
        val lifecycleState = ProcessLifecycleOwner.get().lifecycle.currentStateFlow

        combine(
            lifecycleState,
            syncSettings.foregroundSyncEnabled.flow,
            syncSettings.foregroundSyncInterval.flow,
            upgradeRepo.upgradeInfo,
            syncSettings.backgroundSyncOnMobile.flow,
            networkStateProvider.networkState,
        ) { state, enabled, interval, info, syncOnMobile, networkState ->
            val isActive = state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                && enabled
                && info.isPro
                && (syncOnMobile || !networkState.isMeteredConnection)
            if (isActive) interval else null
        }
            .distinctUntilChanged()
            .flatMapLatest { interval ->
                if (interval == null) {
                    log(TAG) { "Foreground sync inactive" }
                    emptyFlow()
                } else {
                    flow {
                        val intervalMs = interval.coerceAtLeast(5) * 60_000L
                        val elapsed = System.currentTimeMillis() - lastRunAt
                        val initialDelay = (intervalMs - elapsed).coerceAtLeast(0)
                        log(TAG) { "Foreground sync active, interval=${interval}min, initialDelay=${initialDelay}ms" }
                        kotlinx.coroutines.delay(initialDelay)
                        while (true) {
                            log(TAG) { "Foreground sync executing..." }
                            syncExecutor.execute("ForegroundSync")
                            lastRunAt = System.currentTimeMillis()
                            emit(Unit)
                            kotlinx.coroutines.delay(intervalMs)
                        }
                    }
                }
            }
            .setupCommonEventHandlers(TAG) { "foreground-sync" }
            .launchIn(scope)
    }

    companion object {
        private val TAG = logTag("Sync", "Foreground", "Control")
    }
}
