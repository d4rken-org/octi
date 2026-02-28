package eu.darken.octi.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel4
import eu.darken.octi.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

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
        upgradeRepo.launchGithubSponsorsUpgrade()
        navUp()
    }

    companion object {
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}
