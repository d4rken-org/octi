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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scope observer that force-refreshes Glance widgets when Pro entitlement transitions
 * outside of the upgrade screen — e.g. a Play subscription expires while the user isn't
 * looking at [UpgradeViewModel], or a sync detects a Pro grant.
 *
 * `UpgradeViewModel` already refreshes widgets on its own isPro emission, but only while it
 * is alive. This observer covers the rest of the app's lifetime. The initial emission is
 * dropped — widgets are repainted by Glance on attach anyway, so refreshing them on every
 * cold start is wasted work; only subsequent transitions need a force-update.
 */
@Singleton
class UpgradeEntitlementObserver @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val upgradeRepo: UpgradeRepo,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
) {

    fun start() {
        log(TAG) { "start()" }
        upgradeRepo.upgradeInfo
            .map { it.isPro }
            .distinctUntilChanged()
            .drop(1)
            .onEach { isPro ->
                log(TAG, INFO) { "Pro entitlement transitioned: isPro=$isPro" }
                refreshAllWidgets()
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
    }
}
