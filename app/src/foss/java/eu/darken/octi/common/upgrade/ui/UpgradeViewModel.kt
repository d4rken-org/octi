package eu.darken.octi.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.Logging.Priority.ERROR
import eu.darken.octi.common.debug.logging.asLog
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.core.UpgradeRepoFoss
import eu.darken.octi.common.widget.WidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val snackbarEvents = SingleEventFlow<Unit>()

    private var initialized = false
    private val widgetsRefreshedForUpgrade = AtomicBoolean(false)

    fun initialize(forced: Boolean) {
        if (initialized) return
        initialized = true

        upgradeRepo.upgradeInfo
            .filter { it.isPro }
            .take(1)
            .onEach {
                refreshWidgetsForUpgrade()
                if (!forced) navUp()
            }
            .launchInViewModel()
    }

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        handle["browserOpenedAt"] = Clock.System.now().toEpochMilliseconds()
        upgradeRepo.openSponsorsPage()
    }

    fun onResumed() {
        val openedAt = handle.get<Long>("browserOpenedAt") ?: return
        handle["browserOpenedAt"] = null

        val elapsed = Clock.System.now().toEpochMilliseconds() - openedAt
        log(TAG) { "onResumed(): elapsed=${elapsed}ms" }

        if (elapsed.milliseconds < MIN_SPONSOR_TIME) {
            log(TAG) { "onResumed(): too fast, showing snackbar" }
            snackbarEvents.tryEmit(Unit)
        } else {
            log(TAG) { "onResumed(): unlocking upgrade" }
            launch {
                upgradeRepo.unlockUpgrade()
                refreshWidgetsForUpgrade()
                navUp()
            }
        }
    }

    private suspend fun refreshWidgetsForUpgrade() {
        if (!widgetsRefreshedForUpgrade.compareAndSet(false, true)) return

        log(TAG) { "refreshWidgetsForUpgrade()" }
        for (manager in widgetManagers) {
            try {
                manager.refreshWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, ERROR) { "Failed to refresh widgets after upgrade: ${e.asLog()}" }
            }
        }
    }

    companion object {
        private val MIN_SPONSOR_TIME = 5.seconds
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
