package eu.darken.octi.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.navigation.navArgs
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.common.upgrade.core.UpgradeRepoFoss
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoFoss,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<UpgradeFragmentArgs>()

    init {
        if (!navArgs.forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { popNavStack() }
                .launchInViewModel()
        }
    }

    fun goGithubSponsors() {
        log(TAG) { "goGithubSponsors()" }
        upgradeRepo.launchGithubSponsorsUpgrade()
        popNavStack()
    }

    companion object {
        private val TAG = logTag("Upgrade", "ViewModel")
    }
}