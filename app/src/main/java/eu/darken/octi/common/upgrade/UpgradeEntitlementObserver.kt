package eu.darken.octi.common.upgrade

import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.Logging.Priority.INFO
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.widget.WidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * App-scope observer that force-refreshes Glance widgets when Pro entitlement transitions
 * outside of the upgrade screen — e.g. a Play subscription expires while the user isn't
 * looking at the upgrade screen, or a sync detects a Pro grant.
 *
 * The upgrade screen already refreshes widgets on its own emissions, but only while it is alive.
 * This observer covers the rest of the app's lifetime. The refresh keys off the HARD-LOCK boundary
 * (widget-lock on ⇄ off), not raw `isPro`: an unsettled cold-start seed or a billing error is not a
 * lock, so widgets aren't repainted (or falsely locked) on those transitions. The initial emission
 * is dropped — widgets are repainted by Glance on attach anyway, so refreshing them on every cold
 * start is wasted work; only subsequent boundary changes need a force-update.
 */
@Singleton
class UpgradeEntitlementObserver @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val upgradeRepo: UpgradeRepo,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
) {

    // Retained so start() is idempotent — a second call while the observer is already running is a
    // no-op rather than a second, duplicated subscription.
    private var job: Job? = null

    fun start() {
        log(TAG) { "start()" }
        if (job?.isActive == true) {
            log(TAG) { "Already running, ignoring start()." }
            return
        }
        job = upgradeRepo.upgradeInfo
            .map { it.isHardLocked() }
            .distinctUntilChanged()
            .drop(1)
            .onEach { isHardLocked ->
                log(TAG, INFO) { "Pro hard-lock boundary transitioned: isHardLocked=$isHardLocked" }
                refreshAllWidgets()
            }
            // AppCoroutineScope has no exception handler, so a raw throw from the upstream billing
            // flow could take down the process — and a plain .catch would silently END the observer
            // after the first error. Resubscribe with bounded backoff instead so the observer keeps
            // covering entitlement transitions for the whole app lifetime.
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) return@retryWhen false
                val backoff = (BASE_BACKOFF * (1 shl attempt.coerceIn(0L, 5L).toInt()))
                    .coerceAtMost(MAX_BACKOFF)
                log(TAG, ERROR) {
                    "upgradeInfo collection failed (attempt=$attempt), resubscribing in $backoff: ${cause.asLog()}"
                }
                delay(backoff)
                true
            }
            .launchIn(appScope)
    }

    private suspend fun refreshAllWidgets() {
        for (manager in widgetManagers) {
            try {
                manager.refreshWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to refresh widgets after entitlement change: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Upgrade", "Entitlement", "Observer")
        private val BASE_BACKOFF = 1.seconds
        private val MAX_BACKOFF = 1.minutes
    }
}
