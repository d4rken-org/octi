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
import eu.darken.octi.common.upgrade.core.FossUpgrade
import eu.darken.octi.common.upgrade.core.UpgradeRepoFoss
import eu.darken.octi.common.widget.WidgetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
    private val widgetManagers: Set<@JvmSuppressWildcards WidgetManager>,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val snackbarEvents = SingleEventFlow<Unit>()

    private var initialized = false
    private var forced = false
    private var manage = false
    private val widgetsRefreshedForUpgrade = AtomicBoolean(false)

    private val manageRoute = MutableStateFlow<Boolean?>(null)
    private val viewingOffers = MutableStateFlow(handle.get<Boolean>(KEY_SHOW_OFFERS) ?: false)

    data class State(
        val isPro: Boolean = false,
        val upgradedAt: Instant? = null,
        val upgradeType: FossUpgrade.Type? = null,
        val manageMode: Boolean = false,
        val viewingOffers: Boolean = false,
    ) {
        // Free user opening the status row: show the calm status page first, revealing the sponsor
        // pitch only when asked.
        val showFreeStatus: Boolean get() = !isPro && manageMode && !viewingOffers
    }

    // Null until DataStore answered: a defaulted isPro=false would flash the sales pitch (and its
    // armable unlock heuristic) at an existing supporter opening their status.
    val state: StateFlow<State?> = combine(
        upgradeRepo.upgradeInfo,
        manageRoute.filterNotNull(),
        viewingOffers,
    ) { info, isManage, offers ->
        val fossInfo = info as? UpgradeRepoFoss.Info
        State(
            isPro = info.isPro,
            upgradedAt = info.upgradedAt,
            upgradeType = fossInfo?.fossUpgradeType,
            manageMode = isManage,
            viewingOffers = offers,
        )
    }.stateIn(vmScope, SharingStarted.WhileSubscribed(5_000), null)

    fun initialize(forced: Boolean, manage: Boolean) {
        if (initialized) return
        initialized = true
        this.forced = forced
        this.manage = manage
        manageRoute.value = manage

        // Sales route: close once the user is Pro. Manage/forced route: never auto-close.
        if (forced || manage) return
        upgradeRepo.upgradeInfo
            .filter { it.isPro }
            .take(1)
            .onEach {
                refreshWidgetsForUpgrade()
                navUp()
            }
            .launchInViewModel()
    }

    fun onSeeUpgradeOptions() {
        log(TAG) { "onSeeUpgradeOptions()" }
        handle[KEY_SHOW_OFFERS] = true
        viewingOffers.value = true
    }

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        handle["browserOpenedAt"] = Clock.System.now().toEpochMilliseconds()
        upgradeRepo.openSponsorsPage()
    }

    // Plain sponsor link for existing supporters: must NOT arm the unlock heuristic — re-running it
    // would rewrite the "supporter since" date and navigate away from the status view.
    fun openSponsors() {
        log(TAG) { "openSponsors()" }
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
                // Sales route closes; manage/forced route stays to show the new supporter status.
                if (!forced && !manage) navUp()
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
        private const val KEY_SHOW_OFFERS = "upgrade.manage.showOffers"
        private val TAG = logTag("Upgrade", "Foss", "ViewModel")
    }
}
