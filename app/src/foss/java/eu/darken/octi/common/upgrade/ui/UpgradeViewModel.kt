package eu.darken.octi.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.SingleEventFlow
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    val snackbarEvents = SingleEventFlow<Unit>()

    private var initialized = false

    fun initialize(forced: Boolean) {
        if (initialized) return
        initialized = true

        if (!forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { navUp() }
                .launchInViewModel()
        }
    }

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        handle["browserOpenedAt"] = System.currentTimeMillis()
        upgradeRepo.openSponsorsPage()
    }

    fun onResumed() {
        val openedAt = handle.get<Long>("browserOpenedAt") ?: return
        handle["browserOpenedAt"] = null

        val elapsed = System.currentTimeMillis() - openedAt
        log(TAG) { "onResumed(): elapsed=${elapsed}ms" }

        if (elapsed < MIN_SPONSOR_TIME_MS) {
            log(TAG) { "onResumed(): too fast, showing snackbar" }
            snackbarEvents.tryEmit(Unit)
        } else {
            log(TAG) { "onResumed(): unlocking upgrade" }
            upgradeRepo.unlockUpgrade()
            navUp()
        }
    }

    companion object {
        private const val MIN_SPONSOR_TIME_MS = 5_000L
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
