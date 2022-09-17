package eu.darken.octi.main.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.uix.ViewModel3
import eu.darken.octi.main.core.GeneralSettings
import eu.darken.octi.main.ui.dashboard.items.WelcomeVH
import eu.darken.octi.main.ui.dashboard.items.perdevice.DeviceVH
import eu.darken.octi.meta.core.MetaRepo
import eu.darken.octi.sync.core.SyncManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DashboardVM @Inject constructor(
    handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val syncManager: SyncManager,
    private val metaRepo: MetaRepo,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    data class State(
        val items: List<DashboardAdapter.Item>,
    )

    private val refreshTicker = flow {
        while (currentCoroutineContext().isActive) {
            emit(Instant.now())
            if (BuildConfigWrap.DEBUG) {
                delay(1000)
            } else {
                delay(60 * 1000)
            }
        }
    }

    val listItems: LiveData<State> = combine(
        refreshTicker,
        generalSettings.isWelcomeDismissed.flow,
        metaRepo.state,
    ) { now, isWelcomeDismissed, metaState ->
        val items = mutableListOf<DashboardAdapter.Item>()

        if (!isWelcomeDismissed) {
            WelcomeVH.Item(
                onDismiss = { generalSettings.isWelcomeDismissed.value = true }
            ).run { items.add(this) }
        }

        metaState.all.forEach { metaContainer ->
            DeviceVH.Item(
                now = now,
                meta = metaContainer,
            ).run { items.add(this) }
        }

        State(items = items)
    }.asLiveData2()

    init {
        refresh()
    }

    fun goToSyncServices() = launch {
        log(TAG) { "goToSyncServices()" }
        if (syncManager.connectors.first().isEmpty()) {
            log(TAG) { "Connectors are empty, going to add screen directly." }
            DashboardFragmentDirections.actionDashFragmentToSyncAddFragment().navigate()
        } else {
            DashboardFragmentDirections.actionDashFragmentToSyncListFragment().navigate()
        }
    }

    fun refresh() = launch {
        log(TAG) { "refresh()" }
        syncManager.sync()
    }

    companion object {
        private val TAG = logTag("Dashboard", "Fragment", "VM")
    }
}